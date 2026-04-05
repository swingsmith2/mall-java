package com.mall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中等并发 API 压测（默认约 30～50 并行），需本机 Docker。
 */
class MediumConcurrencyApiIT extends MallIntegrationTestBase {

    private static final int CONCURRENT_READS = 40;
    private static final int CONCURRENT_LOGINS = 35;
    private static final int CONCURRENT_ORDERS = 25;

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void concurrentPublicProductApis() throws Exception {
        HttpClient client = newClient();
        String base = baseUrl();
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_READS);

        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_READS; i++) {
            final int page = (i % 3) + 1;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(base + "/api/products?page=" + page + "&size=20"))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();
                    return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, pool));
        }

        for (CompletableFuture<Integer> f : futures) {
            assertThat(f.join()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void concurrentLoginsAndAuthenticatedCart() throws Exception {
        HttpClient client = newClient();
        String base = baseUrl();
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_LOGINS);

        List<CompletableFuture<String>> tokens = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_LOGINS; i++) {
            tokens.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String body = "{\"username\":\"demo\",\"password\":\"demo123\"}";
                    HttpRequest login = HttpRequest.newBuilder()
                            .uri(URI.create(base + "/api/auth/login"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    HttpResponse<String> res = client.send(login, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    assertThat(res.statusCode()).isEqualTo(200);
                    JsonNode root = objectMapper.readTree(res.body());
                    assertThat(root.path("code").asInt()).isZero();
                    return root.path("data").path("token").asText();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, pool));
        }

        List<String> jwtList = tokens.stream().map(CompletableFuture::join).toList();
        assertThat(jwtList).hasSize(CONCURRENT_LOGINS).doesNotContainNull().doesNotContain("");

        List<CompletableFuture<Integer>> cartCalls = new ArrayList<>();
        for (String token : jwtList) {
            cartCalls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    HttpRequest cart = HttpRequest.newBuilder()
                            .uri(URI.create(base + "/api/cart"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build();
                    return client.send(cart, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, pool));
        }
        for (CompletableFuture<Integer> f : cartCalls) {
            assertThat(f.join()).isEqualTo(200);
        }

        pool.shutdown();
        assertThat(pool.awaitTermination(90, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void concurrentOrdersAtomicStock() throws Exception {
        HttpClient client = newClient();
        String base = baseUrl();
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_ORDERS);

        List<CompletableFuture<Long>> orderIds = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_ORDERS; i++) {
            final String suffix = UUID.randomUUID().toString();
            orderIds.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String username = "load_" + suffix.replace("-", "");
                    String regBody = String.format(
                            "{\"username\":\"%s\",\"password\":\"demo123456\"}",
                            username);
                    HttpRequest reg = HttpRequest.newBuilder()
                            .uri(URI.create(base + "/api/auth/register"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(regBody))
                            .build();
                    HttpResponse<String> regRes = client.send(reg, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    assertThat(regRes.statusCode()).isEqualTo(200);

                    String loginBody = String.format(
                            "{\"username\":\"%s\",\"password\":\"demo123456\"}",
                            username);
                    HttpRequest login = HttpRequest.newBuilder()
                            .uri(URI.create(base + "/api/auth/login"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                            .build();
                    HttpResponse<String> loginRes = client.send(login, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    assertThat(loginRes.statusCode()).isEqualTo(200);
                    JsonNode loginRoot = objectMapper.readTree(loginRes.body());
                    String token = loginRoot.path("data").path("token").asText();

                    String orderBody = String.format(
                            "{\"items\":[{\"productId\":1,\"qty\":1}],\"idempotentKey\":\"idem-%s\"}",
                            suffix);
                    HttpRequest order = HttpRequest.newBuilder()
                            .uri(URI.create(base + "/api/orders"))
                            .timeout(Duration.ofSeconds(60))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + token)
                            .POST(HttpRequest.BodyPublishers.ofString(orderBody))
                            .build();
                    HttpResponse<String> orderRes = client.send(order, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    assertThat(orderRes.statusCode()).isEqualTo(200);
                    JsonNode orderRoot = objectMapper.readTree(orderRes.body());
                    assertThat(orderRoot.path("code").asInt()).isZero();
                    return orderRoot.path("data").path("id").asLong();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, pool));
        }

        List<Long> ids = orderIds.stream().map(CompletableFuture::join).toList();
        assertThat(ids).hasSize(CONCURRENT_ORDERS).doesNotContain(0L);
        assertThat(ids.stream().distinct().count()).isEqualTo(CONCURRENT_ORDERS);

        pool.shutdown();
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void concurrentProductDetailCacheWarm() throws Exception {
        HttpClient client = newClient();
        String base = baseUrl();
        int n = 30;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<CompletableFuture<Integer>> futures = IntStream.range(0, n)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        long pid = (i % 3) + 1L;
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(base + "/api/products/" + pid))
                                .timeout(Duration.ofSeconds(30))
                                .GET()
                                .build();
                        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).statusCode();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, pool))
                .toList();
        for (CompletableFuture<Integer> f : futures) {
            assertThat(f.join()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
}
