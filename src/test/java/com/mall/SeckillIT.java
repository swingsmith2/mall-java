package com.mall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.service.SeckillOrderWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class SeckillIT extends MallIntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    SeckillOrderWorker seckillOrderWorker;

    @Test
    void redisPreDeductThenAsyncPersistAndCancelReturnsStock() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String admin = login(client, "admin", "demo123");
        int before = stock(client, 2);
        long activityId = createActivity(client, admin, 2, 5);

        assertThat(stock(client, 2)).isEqualTo(before - 5);
        JsonNode activity = getJson(client, "GET", "/api/seckill/activities/" + activityId, null, null);
        assertThat(activity.path("data").path("remaining").asInt()).isEqualTo(5);

        int buyers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        List<Future<Attempt>> attempts = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            attempts.add(pool.submit(buyOnce(client)));
        }
        List<Attempt> queued = new ArrayList<>();
        int rejected = 0;
        for (Future<Attempt> future : attempts) {
            Attempt attempt = future.get();
            if (attempt.status == 200) {
                queued.add(attempt);
            } else {
                assertThat(attempt.status).isEqualTo(409);
                rejected++;
            }
        }
        pool.shutdown();
        assertThat(queued).hasSize(5);
        assertThat(rejected).isEqualTo(3);

        Attempt first = queued.get(0);
        HttpResponse<String> replay = send(client, "POST", "/api/seckill/activities/" + activityId + "/orders",
                first.tokenUser, first.body);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(replay.body()).path("data").path("token").asText()).isEqualTo(first.seckillToken);

        HttpResponse<String> overLimit = send(client, "POST", "/api/seckill/activities/" + activityId + "/orders",
                first.tokenUser, "{\"qty\":1,\"idempotentKey\":\"again-" + UUID.randomUUID() + "\"}");
        assertThat(overLimit.statusCode()).isEqualTo(409);

        assertThat(seckillOrderWorker.poll()).isEqualTo(5);
        JsonNode done = getJson(client, "GET", "/api/seckill/orders/" + first.seckillToken, first.tokenUser, null);
        assertThat(done.path("data").path("status").asText()).isEqualTo("SUCCESS");
        long orderId = done.path("data").path("orderId").asLong();
        assertThat(orderId).isPositive();
        assertThat(stock(client, 2)).isEqualTo(before - 5);

        HttpResponse<String> cancelled = send(client, "POST", "/api/orders/" + orderId + "/cancel", first.tokenUser, "");
        assertThat(cancelled.statusCode()).as(cancelled.body()).isEqualTo(200);
        JsonNode afterCancel = getJson(client, "GET", "/api/seckill/activities/" + activityId, null, null);
        assertThat(afterCancel.path("data").path("remaining").asInt()).isEqualTo(1);

        String lateUser = registerAndLogin(client);
        HttpResponse<String> recovered = send(client, "POST", "/api/seckill/activities/" + activityId + "/orders",
                lateUser, "{\"qty\":1,\"idempotentKey\":\"back-" + UUID.randomUUID() + "\"}");
        assertThat(recovered.statusCode()).as(recovered.body()).isEqualTo(200);
        assertThat(seckillOrderWorker.poll()).isEqualTo(1);
        assertThat(stock(client, 2)).isEqualTo(before - 5);
        JsonNode closedRemaining = getJson(client, "GET", "/api/seckill/activities/" + activityId, null, null);
        assertThat(closedRemaining.path("data").path("remaining").asInt()).isZero();
    }

    private Callable<Attempt> buyOnce(HttpClient client) {
        return () -> {
            String user = registerAndLogin(client);
            String idem = "sk-" + UUID.randomUUID();
            String body = "{\"qty\":1,\"idempotentKey\":\"" + idem + "\"}";
            HttpResponse<String> res = send(client, "POST", "/api/seckill/activities/" + activityIdHolder[0] + "/orders", user, body);
            Attempt attempt = new Attempt();
            attempt.status = res.statusCode();
            attempt.tokenUser = user;
            attempt.body = body;
            if (res.statusCode() == 200) {
                attempt.seckillToken = objectMapper.readTree(res.body()).path("data").path("token").asText();
            }
            return attempt;
        };
    }

    private final long[] activityIdHolder = new long[1];

    private long createActivity(HttpClient client, String admin, long productId, int seckillStock) throws Exception {
        String body = """
                {"productId":%d,"seckillPriceCent":1000,"stock":%d,"perUserLimit":1,"startAt":"%s","endAt":"%s"}
                """.formatted(productId, seckillStock,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600));
        HttpResponse<String> res = send(client, "POST", "/api/admin/seckill/activities", admin, body);
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        long id = objectMapper.readTree(res.body()).path("data").path("id").asLong();
        activityIdHolder[0] = id;
        return id;
    }

    private int stock(HttpClient client, long productId) throws Exception {
        JsonNode json = getJson(client, "GET", "/api/products/" + productId, null, null);
        return json.path("data").path("stock").asInt();
    }

    private JsonNode getJson(HttpClient client, String method, String path, String token, String body) throws Exception {
        HttpResponse<String> res = send(client, method, path, token, body);
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        return objectMapper.readTree(res.body());
    }

    private String registerAndLogin(HttpClient client) throws Exception {
        String username = "sk_" + UUID.randomUUID().toString().replace("-", "");
        HttpResponse<String> reg = send(client, "POST", "/api/auth/register", null,
                "{\"username\":\"" + username + "\",\"password\":\"demo123456\"}");
        assertThat(reg.statusCode()).as(reg.body()).isEqualTo(200);
        return login(client, username, "demo123456");
    }

    private String login(HttpClient client, String username, String password) throws Exception {
        HttpResponse<String> res = send(client, "POST", "/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        return objectMapper.readTree(res.body()).path("data").path("token").asText();
    }

    private HttpResponse<String> send(HttpClient client, String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            builder.GET();
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static final class Attempt {
        int status;
        String tokenUser;
        String seckillToken;
        String body;
    }
}
