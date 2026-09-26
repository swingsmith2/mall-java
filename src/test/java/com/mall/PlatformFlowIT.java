package com.mall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.payment.PaymentSigner;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformFlowIT extends MallIntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PaymentSigner paymentSigner;
    @Autowired
    SeckillOrderWorker seckillOrderWorker;

    @Test
    void merchantShopSimulatedPayDisputeAndForceClose() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String buyerName = "buyer_" + UUID.randomUUID().toString().replace("-", "");
        HttpResponse<String> registered = send(client, "POST", "/api/auth/register", null,
                "{\"username\":\"" + buyerName + "\",\"password\":\"demo123456\"}");
        assertThat(registered.statusCode()).as(registered.body()).isEqualTo(200);
        String buyer = tokenOf(loginRaw(client, buyerName, "demo123456"));

        String merchantName = "shop_" + UUID.randomUUID().toString().replace("-", "");
        HttpResponse<String> merchantReg = send(client, "POST", "/api/merchant/register", null,
                "{\"username\":\"" + merchantName + "\",\"password\":\"demo123456\",\"shopName\":\"正经店\",\"description\":\"日用\"}");
        assertThat(merchantReg.statusCode()).as(merchantReg.body()).isEqualTo(200);
        JsonNode session = objectMapper.readTree(merchantReg.body()).path("data");
        String merchant = session.path("token").asText();
        long shopId = session.path("shopId").asLong();
        assertThat(session.path("role").asText()).isEqualTo("MERCHANT");

        JsonNode shop = ok(client, "GET", "/api/merchant/shop", merchant, null);
        assertThat(shop.path("data").path("id").asLong()).isEqualTo(shopId);
        assertThat(shop.path("data").path("status").asText()).isEqualTo("OPEN");

        long productId = ok(client, "POST", "/api/merchant/products", merchant,
                "{\"categoryId\":1,\"name\":\"平台商品\",\"priceCent\":19900,\"stock\":20}")
                .path("data").asLong();
        JsonNode catalog = ok(client, "GET", "/api/products?page=1&size=100", null, null);
        assertThat(catalog.path("data").path("records").findValues("id").toString()).contains(String.valueOf(productId));
        JsonNode detail = ok(client, "GET", "/api/products/" + productId, null, null);
        assertThat(detail.path("data").path("stock").asInt()).isEqualTo(20);
        assertThat(detail.path("data").path("shopId").asLong()).isEqualTo(shopId);

        ok(client, "POST", "/api/cart/items", buyer, "{\"productId\":" + productId + ",\"qty\":2}");
        JsonNode cart = ok(client, "GET", "/api/cart", buyer, null);
        assertThat(cart.path("data").get(0).path("qty").asInt()).isEqualTo(2);
        ok(client, "DELETE", "/api/cart/items/" + productId, buyer, null);
        assertThat(ok(client, "GET", "/api/cart", buyer, null).path("data")).isEmpty();

        long cancelId = place(client, buyer, productId, "cancel-" + UUID.randomUUID());
        JsonNode cancelled = ok(client, "POST", "/api/orders/" + cancelId + "/cancel", buyer, "");
        assertThat(cancelled.path("data").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(stock(client, productId)).isEqualTo(20);

        long alipayOrder = place(client, buyer, productId, "ali-" + UUID.randomUUID());
        JsonNode alipay = pay(client, buyer, alipayOrder, "ALIPAY");
        assertThat(alipay.path("status").asText()).isEqualTo("PAID");
        assertThat(alipay.path("payload").path("alipay_trade_pay_response").path("code").asText()).isEqualTo("10000");
        assertThat(alipay.path("payload").path("alipay_trade_pay_response").path("trade_status").asText()).isEqualTo("TRADE_SUCCESS");
        assertThat(alipay.path("payload").path("alipay_trade_pay_response").path("total_amount").asText()).isEqualTo("199.00");
        assertThat(alipay.path("payload").path("sign").asText()).isEqualTo("SIMULATED");

        long wechatOrder = place(client, buyer, productId, "wx-" + UUID.randomUUID());
        JsonNode wechat = pay(client, buyer, wechatOrder, "WECHAT");
        assertThat(wechat.path("payload").path("return_code").asText()).isEqualTo("SUCCESS");
        assertThat(wechat.path("payload").path("result_code").asText()).isEqualTo("SUCCESS");
        assertThat(wechat.path("payload").path("trade_state").asText()).isEqualTo("SUCCESS");
        assertThat(wechat.path("payload").path("total_fee").asInt()).isEqualTo(19900);

        long bankOrder = place(client, buyer, productId, "bank-" + UUID.randomUUID());
        JsonNode bank = pay(client, buyer, bankOrder, "BANK_CARD");
        assertThat(bank.path("payload").path("respCode").asText()).isEqualTo("00");
        assertThat(bank.path("payload").path("respMsg").asText()).isEqualTo("交易成功");
        assertThat(bank.path("payload").path("currencyCode").asText()).isEqualTo("156");
        assertThat(bank.path("payload").path("txnAmt").asText()).isEqualTo("19900");
        assertThat(stock(client, productId)).isEqualTo(17);

        long callbackOrder = place(client, buyer, productId, "cb-" + UUID.randomUUID());
        String paymentNo = "cb-" + callbackOrder;
        String sig = paymentSigner.sign(callbackOrder, paymentNo, 19900L, "SUCCESS");
        HttpResponse<String> callback = callback(client, callbackOrder, paymentNo, 19900L, sig);
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(200);
        assertThat(objectMapper.readTree(callback.body()).path("data").path("status").asText()).isEqualTo("PAID");

        JsonNode mine = ok(client, "GET", "/api/orders/mine?limit=20", buyer, null);
        assertThat(mine.path("data").toString()).contains("PAID");

        JsonNode refundDispute = ok(client, "POST", "/api/orders/" + alipayOrder + "/disputes", buyer, "{\"reason\":\"没收到货\"}");
        long refundDisputeId = refundDispute.path("data").path("id").asLong();
        JsonNode rejectDispute = ok(client, "POST", "/api/orders/" + wechatOrder + "/disputes", buyer, "{\"reason\":\"描述不符\"}");
        long rejectDisputeId = rejectDispute.path("data").path("id").asLong();

        assertThat(send(client, "GET", "/api/cs/disputes?status=OPEN", buyer, null).statusCode()).isEqualTo(403);
        String admin = tokenOf(loginRaw(client, "admin", "demo123"));
        assertThat(send(client, "POST", "/api/cs/disputes/" + refundDisputeId + "/resolve", admin,
                "{\"action\":\"REFUND\",\"note\":\"超管不能裁\"}").statusCode()).isEqualTo(403);
        assertThat(send(client, "POST", "/api/admin/shops/" + shopId + "/force-close", merchant,
                "{\"reason\":\"商家不能关店\"}").statusCode()).isEqualTo(403);
        assertThat(send(client, "GET", "/api/admin/ops/alerts", merchant, null).statusCode()).isEqualTo(403);

        String cs = tokenOf(loginRaw(client, "cs", "demo123"));
        JsonNode openList = ok(client, "GET", "/api/cs/disputes?status=OPEN", cs, null);
        assertThat(openList.path("data").toString()).contains(String.valueOf(refundDisputeId));
        JsonNode refunded = ok(client, "POST", "/api/cs/disputes/" + refundDisputeId + "/resolve", cs,
                "{\"action\":\"REFUND\",\"note\":\"同意退款\"}");
        assertThat(refunded.path("data").path("status").asText()).isEqualTo("RESOLVED");
        assertThat(refunded.path("data").path("resolution").asText()).isEqualTo("REFUND");
        JsonNode rejected = ok(client, "POST", "/api/cs/disputes/" + rejectDisputeId + "/resolve", cs,
                "{\"action\":\"REJECT\",\"note\":\"证据不足\"}");
        assertThat(rejected.path("data").path("resolution").asText()).isEqualTo("REJECT");

        JsonNode orders = ok(client, "GET", "/api/orders/mine?limit=20", buyer, null);
        assertThat(statusOf(orders, alipayOrder)).isEqualTo("REFUNDED");
        assertThat(statusOf(orders, wechatOrder)).isEqualTo("PAID");
        assertThat(stock(client, productId)).isEqualTo(17);

        long seckillProduct = ok(client, "POST", "/api/merchant/products", merchant,
                "{\"categoryId\":1,\"name\":\"秒杀款\",\"priceCent\":5000,\"stock\":8}")
                .path("data").asLong();
        String window = """
                {"productId":%d,"seckillPriceCent":1000,"stock":2,"perUserLimit":1,"startAt":"%s","endAt":"%s"}
                """.formatted(seckillProduct, Instant.now().minusSeconds(5), Instant.now().plusSeconds(3600));
        long activityId = ok(client, "POST", "/api/merchant/seckill/activities", merchant, window)
                .path("data").path("id").asLong();
        assertThat(ok(client, "GET", "/api/seckill/activities/" + activityId, null, null)
                .path("data").path("remaining").asInt()).isEqualTo(2);
        HttpResponse<String> queued = send(client, "POST", "/api/seckill/activities/" + activityId + "/orders", buyer,
                "{\"qty\":1,\"idempotentKey\":\"flow-" + UUID.randomUUID() + "\"}");
        assertThat(queued.statusCode()).as(queued.body()).isEqualTo(200);
        String seckillToken = objectMapper.readTree(queued.body()).path("data").path("token").asText();
        assertThat(seckillOrderWorker.poll()).isEqualTo(1);
        JsonNode seckillDone = ok(client, "GET", "/api/seckill/orders/" + seckillToken, buyer, null);
        assertThat(seckillDone.path("data").path("status").asText()).isEqualTo("SUCCESS");

        assertThat(send(client, "POST", "/api/admin/shops/" + shopId + "/force-close", cs,
                "{\"reason\":\"客服不能关店\"}").statusCode()).isEqualTo(403);
        JsonNode closed = ok(client, "POST", "/api/admin/shops/" + shopId + "/force-close", admin,
                "{\"reason\":\"售假\"}");
        assertThat(closed.path("data").path("status").asText()).isEqualTo("FORCED_CLOSED");
        JsonNode shops = ok(client, "GET", "/api/admin/shops", admin, null);
        assertThat(shops.path("data").toString()).contains("FORCED_CLOSED");

        HttpResponse<String> blocked = send(client, "POST", "/api/orders", buyer,
                "{\"items\":[{\"productId\":" + productId + ",\"qty\":1}]}");
        assertThat(blocked.statusCode()).isEqualTo(400);
        assertThat(blocked.body()).contains("店铺未营业");
        assertThat(send(client, "GET", "/api/products/" + productId, null, null).statusCode()).isEqualTo(404);
        assertThat(send(client, "POST", "/api/merchant/products", merchant,
                "{\"categoryId\":1,\"name\":\"关店后\",\"priceCent\":100,\"stock\":1}").statusCode()).isEqualTo(400);

        JsonNode alerts = ok(client, "GET", "/api/admin/ops/alerts", admin, null);
        assertThat(alerts.path("data").path("open")).isNotNull();
        assertThat(send(client, "GET", "/api/admin/ops/alerts", cs, null).statusCode()).isEqualTo(403);
        assertThat(send(client, "GET", "/api/cs/disputes", admin, null).statusCode()).isEqualTo(403);
    }

    private long place(HttpClient client, String buyer, long productId, String key) throws Exception {
        JsonNode order = ok(client, "POST", "/api/orders", buyer,
                "{\"idempotentKey\":\"" + key + "\",\"items\":[{\"productId\":" + productId + ",\"qty\":1}]}");
        assertThat(order.path("data").path("status").asText()).isEqualTo("CREATED");
        return order.path("data").path("id").asLong();
    }

    private JsonNode pay(HttpClient client, String buyer, long orderId, String channel) throws Exception {
        JsonNode paid = ok(client, "POST", "/api/orders/" + orderId + "/pay", buyer, "{\"channel\":\"" + channel + "\"}");
        assertThat(paid.path("data").path("channel").asText()).isEqualTo(channel);
        assertThat(paid.path("data").path("orderId").asLong()).isEqualTo(orderId);
        return paid.path("data");
    }

    private int stock(HttpClient client, long productId) throws Exception {
        return ok(client, "GET", "/api/products/" + productId, null, null).path("data").path("stock").asInt();
    }

    private String statusOf(JsonNode orders, long orderId) {
        for (JsonNode order : orders.path("data")) {
            if (order.path("id").asLong() == orderId) {
                return order.path("status").asText();
            }
        }
        throw new AssertionError("订单不在列表中: " + orderId);
    }

    private JsonNode ok(HttpClient client, String method, String path, String token, String body) throws Exception {
        HttpResponse<String> res = send(client, method, path, token, body);
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        return objectMapper.readTree(res.body());
    }

    private HttpResponse<String> loginRaw(HttpClient client, String username, String password) throws Exception {
        HttpResponse<String> res = send(client, "POST", "/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        return res;
    }

    private String tokenOf(HttpResponse<String> login) throws Exception {
        return objectMapper.readTree(login.body()).path("data").path("token").asText();
    }

    private HttpResponse<String> callback(HttpClient client, long orderId, String paymentNo, long amount, String sig) throws Exception {
        String body = "{\"orderId\":" + orderId + ",\"paymentNo\":\"" + paymentNo + "\",\"amountCent\":" + amount + ",\"status\":\"SUCCESS\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/payments/callback"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header(PaymentSigner.HEADER, sig)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(HttpClient client, String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(base() + path))
                .timeout(Duration.ofSeconds(20));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            builder.GET();
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }
}
