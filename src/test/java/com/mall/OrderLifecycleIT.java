package com.mall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.config.MallProperties;
import com.mall.domain.OutboxEvent;
import com.mall.mapper.OutboxMapper;
import com.mall.payment.PaymentSigner;
import com.mall.service.OrderTimeoutJob;
import com.mall.service.OutboxDispatcher;
import com.mall.service.RedisRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderLifecycleIT extends MallIntegrationTestBase {

    @DynamicPropertySource
    static void shortPayWindow(DynamicPropertyRegistry registry) {
        registry.add("mall.order.pay-timeout", () -> "3s");
    }

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PaymentSigner paymentSigner;
    @Autowired
    OutboxDispatcher outboxDispatcher;
    @Autowired
    OrderTimeoutJob orderTimeoutJob;
    @Autowired
    OutboxMapper outboxMapper;
    @Autowired
    RedisRateLimiter rateLimiter;
    @Autowired
    MallProperties mallProperties;

    @Test
    void payCallbackOutboxTimeoutAndCancel() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String token = registerAndLogin(client);
        int before = stock(client);

        String idem = "idem-" + UUID.randomUUID();
        long paidId = place(client, token, idem);
        long again = place(client, token, idem);
        assertThat(again).isEqualTo(paidId);
        assertThat(stock(client)).isEqualTo(before - 1);
        assertThat(statusOf(client, token, paidId)).isEqualTo("CREATED");

        JsonNode paid = callback(client, paidId, "pay-" + paidId, 12900L, paymentSigner.sign(paidId, "pay-" + paidId, 12900L, "SUCCESS"));
        assertThat(paid.path("data").path("status").asText()).isEqualTo("PAID");
        JsonNode replay = callback(client, paidId, "pay-" + paidId, 12900L, paymentSigner.sign(paidId, "pay-" + paidId, 12900L, "SUCCESS"));
        assertThat(replay.path("code").asInt()).isZero();
        assertThat(replay.path("data").path("status").asText()).isEqualTo("PAID");
        assertThat(stock(client)).isEqualTo(before - 1);

        HttpResponse<String> badSig = sendCallback(client, paidId, "pay-other", 12900L, "deadbeef");
        assertThat(badSig.statusCode()).isEqualTo(401);

        long cancelId = place(client, token, "idem-cancel-" + UUID.randomUUID());
        assertThat(stock(client)).isEqualTo(before - 2);
        HttpResponse<String> cancelled = send(client, "POST", "/api/orders/" + cancelId + "/cancel", token, "");
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(cancelled.body()).path("data").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(stock(client)).isEqualTo(before - 1);
        HttpResponse<String> latePay = sendCallback(client, cancelId, "pay-" + cancelId, 12900L,
                paymentSigner.sign(cancelId, "pay-" + cancelId, 12900L, "SUCCESS"));
        assertThat(latePay.statusCode()).isEqualTo(409);

        long expireId = place(client, token, "idem-expire-" + UUID.randomUUID());
        Thread.sleep(3500);
        assertThat(orderTimeoutJob.closeExpired()).isGreaterThanOrEqualTo(1);
        assertThat(statusOf(client, token, expireId)).isEqualTo("CANCELLED");
        assertThat(stock(client)).isEqualTo(before - 1);

        assertThat(outboxDispatcher.poll()).isGreaterThan(0);
        assertThat(outboxMapper.listByAggregate(paidId))
                .extracting(OutboxEvent::getEventType)
                .contains("ORDER_CREATED", "ORDER_PAID");
        assertThat(outboxMapper.listByAggregate(paidId)).allMatch(e -> "SENT".equals(e.getStatus()));
        assertThat(outboxMapper.listByAggregate(cancelId)).extracting(OutboxEvent::getStatus).contains("SENT");
        assertThat(outboxMapper.listByAggregate(expireId)).extracting(OutboxEvent::getEventType).contains("ORDER_CANCELLED");

        String admin = login(client, "admin", "demo123");
        HttpResponse<String> alerts = send(client, "GET", "/api/admin/ops/alerts", admin, null);
        assertThat(alerts.statusCode()).isEqualTo(200);
        JsonNode alert = objectMapper.readTree(alerts.body()).path("data");
        assertThat(alert.path("open").asBoolean()).isFalse();
    }

    @Test
    void loginRateLimit() {
        int previous = mallProperties.getRateLimit().getLoginsPerMinute();
        String user = "rl-" + UUID.randomUUID();
        mallProperties.getRateLimit().setLoginsPerMinute(2);
        try {
            rateLimiter.checkLoginLimit(user);
            rateLimiter.checkLoginLimit(user);
            assertThatThrownBy(() -> rateLimiter.checkLoginLimit(user))
                    .hasMessageContaining("登录过于频繁");
        } finally {
            mallProperties.getRateLimit().setLoginsPerMinute(previous);
        }
    }

    private long place(HttpClient client, String token, String idem) throws Exception {
        String body = "{\"items\":[{\"productId\":3,\"qty\":1}],\"idempotentKey\":\"" + idem + "\"}";
        HttpResponse<String> res = send(client, "POST", "/api/orders", token, body);
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        return objectMapper.readTree(res.body()).path("data").path("id").asLong();
    }

    private String statusOf(HttpClient client, String token, long orderId) throws Exception {
        HttpResponse<String> res = send(client, "GET", "/api/orders/mine?limit=50", token, null);
        assertThat(res.statusCode()).isEqualTo(200);
        for (JsonNode node : objectMapper.readTree(res.body()).path("data")) {
            if (node.path("id").asLong() == orderId) {
                return node.path("status").asText();
            }
        }
        throw new AssertionError("order not found: " + orderId);
    }

    private int stock(HttpClient client) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/products/3"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(res.statusCode()).isEqualTo(200);
        return objectMapper.readTree(res.body()).path("data").path("stock").asInt();
    }

    private JsonNode callback(HttpClient client, long orderId, String paymentNo, long amount, String sig) throws Exception {
        HttpResponse<String> res = sendCallback(client, orderId, paymentNo, amount, sig);
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        return objectMapper.readTree(res.body());
    }

    private HttpResponse<String> sendCallback(HttpClient client, long orderId, String paymentNo, long amount, String sig) throws Exception {
        String body = "{\"orderId\":" + orderId + ",\"paymentNo\":\"" + paymentNo + "\",\"amountCent\":" + amount + ",\"status\":\"SUCCESS\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/payments/callback"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Mall-Signature", sig)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String registerAndLogin(HttpClient client) throws Exception {
        String username = "life_" + UUID.randomUUID().toString().replace("-", "");
        HttpResponse<String> reg = send(client, "POST", "/api/auth/register", null,
                "{\"username\":\"" + username + "\",\"password\":\"demo123456\"}");
        assertThat(reg.statusCode()).isEqualTo(200);
        return login(client, username, "demo123456");
    }

    private String login(HttpClient client, String username, String password) throws Exception {
        HttpResponse<String> res = send(client, "POST", "/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        return objectMapper.readTree(res.body()).path("data").path("token").asText();
    }

    private HttpResponse<String> send(HttpClient client, String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(base() + path))
                .timeout(Duration.ofSeconds(15));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            b.header("Content-Type", "application/json");
            b.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            b.GET();
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }
}
