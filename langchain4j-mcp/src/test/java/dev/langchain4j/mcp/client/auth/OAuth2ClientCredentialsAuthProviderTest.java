package dev.langchain4j.mcp.client.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.exception.HttpException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OAuth2ClientCredentialsAuthProviderTest {

    private static final McpAuthRequest REQUEST =
            new McpAuthRequest("POST", URI.create("https://mcp.example.com/mcp"), null);

    private record TokenRequest(String authorization, String contentType, String body) {}

    private static class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final List<TokenRequest> tokenRequests = new CopyOnWriteArrayList<>();
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<String> responseBody =
            new AtomicReference<>("{\"access_token\":\"abc\",\"token_type\":\"Bearer\",\"expires_in\":60}");
    private final MutableClock clock = new MutableClock();

    private HttpServer server;
    private String tokenEndpoint;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/token", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            tokenRequests.add(new TokenRequest(
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body));
            respond(exchange, responseStatus.get(), responseBody.get());
        });
        server.start();
        tokenEndpoint = "http://localhost:" + server.getAddress().getPort() + "/token";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private OAuth2ClientCredentialsAuthProvider.Builder provider() {
        return OAuth2ClientCredentialsAuthProvider.builder()
                .tokenEndpoint(tokenEndpoint)
                .clientId("my-agent")
                .clientSecret("s3cret")
                .clock(clock);
    }

    private static Map<String, String> formOf(String body) {
        return java.util.Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(
                        kv -> java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv -> java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8),
                        (a, b) -> b,
                        java.util.LinkedHashMap::new));
    }

    @Test
    void obtains_a_token_with_the_client_credentials_grant_and_basic_authentication() {
        McpAuthProvider provider = provider()
                .scopes("mcp:tools", "mcp:resources")
                .resource("https://mcp.example.com/mcp")
                .additionalParameter("audience", "mcp")
                .build();

        assertThat(provider.getAuthorization(REQUEST)).isEqualTo("Bearer abc");

        assertThat(tokenRequests).hasSize(1);
        TokenRequest request = tokenRequests.get(0);
        String expectedBasic =
                "Basic " + Base64.getEncoder().encodeToString("my-agent:s3cret".getBytes(StandardCharsets.UTF_8));
        assertThat(request.authorization()).isEqualTo(expectedBasic);
        assertThat(request.contentType()).isEqualTo("application/x-www-form-urlencoded");
        assertThat(formOf(request.body()))
                .containsExactly(
                        Map.entry("grant_type", "client_credentials"),
                        Map.entry("scope", "mcp:tools mcp:resources"),
                        Map.entry("resource", "https://mcp.example.com/mcp"),
                        Map.entry("audience", "mcp"));
    }

    @Test
    void sends_client_credentials_in_the_body_when_configured() {
        McpAuthProvider provider = provider()
                .clientAuthenticationMethod(
                        OAuth2ClientCredentialsAuthProvider.ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .build();

        provider.getAuthorization(REQUEST);

        TokenRequest request = tokenRequests.get(0);
        assertThat(request.authorization()).isNull();
        assertThat(formOf(request.body()))
                .containsExactly(
                        Map.entry("grant_type", "client_credentials"),
                        Map.entry("client_id", "my-agent"),
                        Map.entry("client_secret", "s3cret"));
    }

    @Test
    void reuses_the_token_until_shortly_before_it_expires() {
        McpAuthProvider provider =
                provider().expirationSkew(Duration.ofSeconds(10)).build();

        provider.getAuthorization(REQUEST);
        clock.advance(Duration.ofSeconds(49));
        provider.getAuthorization(REQUEST);
        assertThat(tokenRequests).hasSize(1);

        responseBody.set("{\"access_token\":\"def\",\"token_type\":\"bearer\"}");
        clock.advance(Duration.ofSeconds(1)); // 60s lifetime - 10s skew reached
        assertThat(provider.getAuthorization(REQUEST)).isEqualTo("Bearer def");
        assertThat(tokenRequests).hasSize(2);

        clock.advance(Duration.ofDays(365)); // no expires_in: valid until a challenge says otherwise
        provider.getAuthorization(REQUEST);
        assertThat(tokenRequests).hasSize(2);
    }

    @Test
    void expiration_skew_is_capped_at_half_the_token_lifetime() {
        responseBody.set("{\"access_token\":\"abc\",\"token_type\":\"Bearer\",\"expires_in\":10}");
        McpAuthProvider provider =
                provider().expirationSkew(Duration.ofSeconds(30)).build();

        provider.getAuthorization(REQUEST);
        clock.advance(Duration.ofSeconds(4));
        provider.getAuthorization(REQUEST);
        assertThat(tokenRequests)
                .as("a skew longer than the lifetime must not discard every token")
                .hasSize(1);

        clock.advance(Duration.ofSeconds(1)); // half the lifetime reached
        provider.getAuthorization(REQUEST);
        assertThat(tokenRequests).hasSize(2);
    }

    @Test
    void concurrent_first_requests_obtain_a_single_token() throws Exception {
        McpAuthProvider provider = provider().build();
        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return provider.getAuthorization(REQUEST);
                }));
            }
            start.countDown();
            for (Future<String> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("Bearer abc");
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(tokenRequests).hasSize(1);
    }

    @Test
    void honours_expires_in_sent_as_a_string() {
        responseBody.set("{\"access_token\":\"abc\",\"token_type\":\"Bearer\",\"expires_in\":\"60\"}");
        McpAuthProvider provider = provider().expirationSkew(Duration.ZERO).build();

        provider.getAuthorization(REQUEST);
        clock.advance(Duration.ofSeconds(59));
        provider.getAuthorization(REQUEST);
        assertThat(tokenRequests).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        provider.getAuthorization(REQUEST);
        assertThat(tokenRequests).hasSize(2);
    }

    @Test
    void a_401_challenge_discards_the_token_so_that_the_retry_uses_a_new_one() {
        McpAuthProvider provider = provider().build();
        provider.getAuthorization(REQUEST);
        responseBody.set("{\"access_token\":\"def\",\"token_type\":\"Bearer\",\"expires_in\":60}");

        boolean retry = provider.onChallenge(
                new McpAuthChallenge(401, REQUEST.uri(), "Bearer", Map.of("error", "invalid_token")));

        assertThat(retry).isTrue();
        assertThat(provider.getAuthorization(REQUEST)).isEqualTo("Bearer def");
        assertThat(tokenRequests).hasSize(2);
    }

    @Test
    void an_insufficient_scope_challenge_adds_the_missing_scopes_and_retries_once() {
        McpAuthProvider provider = provider().scopes("a").build();
        provider.getAuthorization(REQUEST);
        McpAuthChallenge challenge = new McpAuthChallenge(
                403, REQUEST.uri(), "Bearer", Map.of("error", "insufficient_scope", "scope", "b c"));

        assertThat(provider.onChallenge(challenge)).isTrue();
        provider.getAuthorization(REQUEST);
        assertThat(formOf(tokenRequests.get(1).body())).containsEntry("scope", "a b c");

        // the same challenge again means the new scopes did not help: give up rather than loop
        assertThat(provider.onChallenge(challenge)).isFalse();
        assertThat(tokenRequests).hasSize(2);
    }

    @Test
    void a_403_without_scope_guidance_is_not_retried() {
        McpAuthProvider provider = provider().build();

        assertThat(provider.onChallenge(new McpAuthChallenge(403, REQUEST.uri(), "Bearer", Map.of())))
                .isFalse();
        assertThat(provider.onChallenge(new McpAuthChallenge(403, REQUEST.uri(), null, Map.of())))
                .isFalse();
    }

    @Test
    void token_endpoint_error_is_reported_without_leaking_the_secret() {
        responseStatus.set(400);
        responseBody.set("{\"error\":\"invalid_client\"}");
        McpAuthProvider provider = provider().build();

        assertThatThrownBy(() -> provider.getAuthorization(REQUEST))
                .isInstanceOf(HttpException.class)
                .hasMessageContaining("invalid_client")
                .hasMessageContaining(tokenEndpoint)
                .satisfies(e -> assertThat(((HttpException) e).statusCode()).isEqualTo(400))
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void malformed_token_responses_are_rejected() {
        McpAuthProvider provider = provider().build();

        responseBody.set("not json");
        assertThatThrownBy(() -> provider.getAuthorization(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-JSON");

        responseBody.set("{\"token_type\":\"Bearer\"}");
        assertThatThrownBy(() -> provider.getAuthorization(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no access_token");

        responseBody.set("{\"access_token\":\"abc\",\"token_type\":\"MAC\"}");
        assertThatThrownBy(() -> provider.getAuthorization(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAC");
    }

    @Test
    void builder_requires_endpoint_and_credentials() {
        assertThatThrownBy(() -> OAuth2ClientCredentialsAuthProvider.builder()
                        .clientId("id")
                        .clientSecret("secret")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenEndpoint");
        assertThatThrownBy(() -> OAuth2ClientCredentialsAuthProvider.builder()
                        .tokenEndpoint(tokenEndpoint)
                        .clientSecret("secret")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientId");
        assertThatThrownBy(() -> OAuth2ClientCredentialsAuthProvider.builder()
                        .tokenEndpoint(tokenEndpoint)
                        .clientId("id")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientSecret");
    }
}
