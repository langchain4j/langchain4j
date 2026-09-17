package dev.langchain4j.mcp.client.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The provider without a configured token endpoint: it discovers the authorization server from
 * the MCP server's challenge and metadata.
 */
class OAuth2ClientCredentialsAuthProviderDiscoveryTest {

    private record TokenRequest(String authorization, Map<String, String> form) {}

    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final List<TokenRequest> tokenRequests = new CopyOnWriteArrayList<>();
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private final Map<String, String> redirects = new ConcurrentHashMap<>();

    private static final McpAuthRequest REQUEST =
            new McpAuthRequest("POST", URI.create("https://mcp.example.com/mcp"), null);

    private HttpServer server;
    private String base;
    private URI mcpServer;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestedPaths.add(path);
            String body;
            String redirect = redirects.get(path);
            if (redirect != null) {
                exchange.getResponseHeaders().set("Location", base + redirect);
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }
            if (path.endsWith("/token")) {
                String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                tokenRequests.add(
                        new TokenRequest(exchange.getRequestHeaders().getFirst("Authorization"), formOf(form)));
                body = "{\"access_token\":\"tok-" + path + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
            } else {
                body = documents.get(path);
            }
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
        mcpServer = URI.create(base + "/mcp");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static Map<String, String> formOf(String body) {
        return Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        kv -> URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv -> URLDecoder.decode(kv[1], StandardCharsets.UTF_8),
                        (a, b) -> b,
                        LinkedHashMap::new));
    }

    @Test
    void metadata_redirects_are_followed_even_though_the_token_endpoint_client_never_redirects() {
        publish("/issuer", null, "");
        redirects.put("/.well-known/oauth-protected-resource/mcp", "/prm");
        OAuth2ClientCredentialsAuthProvider provider = provider().build();

        assertThat(provider.onChallenge(challenge(Map.of()))).isTrue();

        assertThat(provider.tokenEndpoint()).isEqualTo(URI.create(base + "/issuer/token"));
        assertThat(requestedPaths).contains("/.well-known/oauth-protected-resource/mcp", "/prm");
    }

    @Test
    void an_explicitly_configured_http_client_is_shared_with_discovery() {
        publish("/issuer", null, "");
        List<URI> seen = new CopyOnWriteArrayList<>();
        HttpClient recording = new RecordingHttpClient(seen);
        OAuth2ClientCredentialsAuthProvider provider =
                provider().httpClient(recording).build();

        assertThat(provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm"))))
                .isTrue();
        provider.getAuthorization(REQUEST);

        assertThat(seen.stream().map(URI::getPath))
                .containsExactly("/prm", "/.well-known/oauth-authorization-server/issuer", "/issuer/token");
    }

    /** Delegates to a real client but records every request URI. */
    private static final class RecordingHttpClient extends HttpClient {

        private final HttpClient delegate = HttpClient.newHttpClient();
        private final List<URI> seen;

        RecordingHttpClient(List<URI> seen) {
            this.seen = seen;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            seen.add(request.uri());
            return delegate.send(request, handler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            seen.add(request.uri());
            return delegate.sendAsync(request, handler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushHandler) {
            seen.add(request.uri());
            return delegate.sendAsync(request, handler, pushHandler);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return delegate.cookieHandler();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return delegate.connectTimeout();
        }

        @Override
        public Redirect followRedirects() {
            return delegate.followRedirects();
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return delegate.proxy();
        }

        @Override
        public SSLContext sslContext() {
            return delegate.sslContext();
        }

        @Override
        public SSLParameters sslParameters() {
            return delegate.sslParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return delegate.authenticator();
        }

        @Override
        public Version version() {
            return delegate.version();
        }

        @Override
        public Optional<Executor> executor() {
            return delegate.executor();
        }
    }

    private void publish(String issuerPath, String scopesSupported, String extraAsFields) {
        documents.put(
                "/prm",
                "{\"resource\":\"" + base + "/mcp\",\"authorization_servers\":[\"" + base + issuerPath + "\"]"
                        + (scopesSupported == null ? "" : ",\"scopes_supported\":" + scopesSupported) + "}");
        documents.put(
                "/.well-known/oauth-authorization-server" + issuerPath,
                "{\"issuer\":\"" + base + issuerPath + "\",\"token_endpoint\":\"" + base + issuerPath + "/token\""
                        + extraAsFields + "}");
    }

    private OAuth2ClientCredentialsAuthProvider.Builder provider() {
        return OAuth2ClientCredentialsAuthProvider.builder().clientId("agent").clientSecret("secret");
    }

    private McpAuthChallenge challenge(Map<String, String> parameters) {
        return new McpAuthChallenge(401, mcpServer, "Bearer", parameters);
    }

    @Test
    void an_issuer_the_application_did_not_allow_is_never_contacted_and_no_token_is_requested() {
        publish("/rogue", null, ",\"grant_types_supported\":[\"client_credentials\"]");
        OAuth2ClientCredentialsAuthProvider provider =
                provider().allowedIssuers(base + "/expected").build();

        assertThatThrownBy(() -> provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm"))))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining(base + "/rogue");

        // the client secret was never presented anywhere, and the rogue issuer was not even asked for its metadata
        assertThat(tokenRequests).isEmpty();
        assertThat(provider.tokenEndpoint()).isNull();
        assertThat(requestedPaths).doesNotContain("/.well-known/oauth-authorization-server/rogue");
    }

    @Test
    void an_allowed_issuer_is_used_as_usual() {
        publish("/expected", null, ",\"grant_types_supported\":[\"client_credentials\"]");
        OAuth2ClientCredentialsAuthProvider provider =
                provider().allowedIssuers(List.of(base + "/expected")).build();

        assertThat(provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm"))))
                .isTrue();
        assertThat(provider.getAuthorization(REQUEST)).isEqualTo("Bearer tok-/expected/token");
        assertThat(tokenRequests).hasSize(1);
    }

    @Test
    void a_refused_challenge_leaves_the_token_and_the_scopes_untouched() {
        publish("/expected", null, ",\"grant_types_supported\":[\"client_credentials\"]");
        documents.put(
                "/prm-elsewhere",
                "{\"resource\":\"" + base + "/mcp\",\"authorization_servers\":[\"" + base + "/rogue\"]}");
        documents.put(
                "/.well-known/oauth-authorization-server/rogue",
                "{\"issuer\":\"" + base + "/rogue\",\"token_endpoint\":\"" + base + "/rogue/token\"}");
        OAuth2ClientCredentialsAuthProvider provider =
                provider().allowedIssuers(base + "/expected").build();
        provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm")));
        String token = provider.getAuthorization(REQUEST);

        assertThatThrownBy(() -> provider.onChallenge(
                        challenge(Map.of("resource_metadata", base + "/prm-elsewhere", "scope", "admin:everything"))))
                .isInstanceOf(McpAuthorizationDiscoveryException.class);

        // the refused challenge must not discard the token, widen the scopes or move the endpoint
        assertThat(provider.getAuthorization(REQUEST)).isEqualTo(token);
        assertThat(provider.requestedScopes()).doesNotContain("admin:everything");
        assertThat(provider.tokenEndpoint()).isEqualTo(URI.create(base + "/expected/token"));
        assertThat(tokenRequests).hasSize(1);
    }

    @Test
    void the_allowed_issuers_are_enforced_even_with_a_caller_supplied_discovery() {
        publish("/rogue", null, ",\"grant_types_supported\":[\"client_credentials\"]");
        OAuth2ClientCredentialsAuthProvider provider = provider()
                // a discovery instance that knows nothing about the allowed issuers
                .discovery(McpAuthorizationDiscovery.builder()
                        .timeout(Duration.ofSeconds(5))
                        .build())
                .allowedIssuers(base + "/expected")
                .build();

        assertThatThrownBy(() -> provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm"))))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("not one of the allowed issuers");

        assertThat(tokenRequests).isEmpty();
    }

    @Test
    void sends_nothing_before_discovery_then_discovers_from_the_challenge() {
        publish("/auth", "[\"mcp:tools\"]", ",\"grant_types_supported\":[\"client_credentials\"]");
        OAuth2ClientCredentialsAuthProvider provider = provider().build();
        McpAuthRequest request = new McpAuthRequest("POST", mcpServer, null);

        assertThat(provider.getAuthorization(request)).isNull();
        assertThat(provider.tokenEndpoint()).isNull();
        assertThat(requestedPaths).isEmpty();

        boolean retry = provider.onChallenge(
                challenge(Map.of("resource_metadata", base + "/prm", "scope", "tools:read tools:write")));

        assertThat(retry).isTrue();
        assertThat(provider.tokenEndpoint()).isEqualTo(URI.create(base + "/auth/token"));
        assertThat(provider.getAuthorization(request)).isEqualTo("Bearer tok-/auth/token");
        assertThat(tokenRequests).hasSize(1);
        assertThat(tokenRequests.get(0).authorization()).startsWith("Basic ");
        assertThat(tokenRequests.get(0).form())
                .containsEntry("grant_type", "client_credentials")
                .containsEntry("scope", "tools:read tools:write") // the challenge wins over scopes_supported
                .containsEntry("resource", base + "/mcp");
        assertThat(requestedPaths)
                .containsExactly("/prm", "/.well-known/oauth-authorization-server/auth", "/auth/token");
    }

    @Test
    void uses_scopes_supported_when_the_challenge_names_no_scope() {
        publish("/auth", "[\"a\",\"b\"]", "");
        OAuth2ClientCredentialsAuthProvider provider = provider().build();

        provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm")));
        provider.getAuthorization(new McpAuthRequest("POST", mcpServer, null));

        assertThat(provider.requestedScopes()).containsExactly("a", "b");
        assertThat(tokenRequests.get(0).form()).containsEntry("scope", "a b");
    }

    @Test
    void omits_scope_when_neither_challenge_nor_metadata_name_any() {
        publish("/auth", null, "");
        OAuth2ClientCredentialsAuthProvider provider = provider().build();

        provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm")));
        provider.getAuthorization(new McpAuthRequest("POST", mcpServer, null));

        assertThat(tokenRequests.get(0).form()).doesNotContainKey("scope");
    }

    @Test
    void configured_scopes_and_resource_take_precedence_over_discovered_ones() {
        publish("/auth", "[\"a\"]", "");
        OAuth2ClientCredentialsAuthProvider provider =
                provider().scopes("x").resource("https://mcp.example.com/mcp").build();

        provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm")));
        provider.getAuthorization(new McpAuthRequest("POST", mcpServer, null));

        assertThat(tokenRequests.get(0).form())
                .containsEntry("scope", "x")
                .containsEntry("resource", "https://mcp.example.com/mcp");
    }

    @Test
    void discovers_from_the_well_known_uri_when_the_challenge_has_no_resource_metadata() {
        publish("/auth", null, "");
        documents.put("/.well-known/oauth-protected-resource/mcp", documents.remove("/prm"));
        OAuth2ClientCredentialsAuthProvider provider = provider().build();

        assertThat(provider.onChallenge(challenge(Map.of()))).isTrue();

        assertThat(provider.tokenEndpoint()).isEqualTo(URI.create(base + "/auth/token"));
        assertThat(requestedPaths.get(0)).isEqualTo("/.well-known/oauth-protected-resource/mcp");
    }

    @Test
    void selects_client_secret_post_when_the_server_does_not_support_basic() {
        publish("/auth", null, ",\"token_endpoint_auth_methods_supported\":[\"client_secret_post\"]");
        OAuth2ClientCredentialsAuthProvider provider = provider().build();

        provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm")));
        provider.getAuthorization(new McpAuthRequest("POST", mcpServer, null));

        assertThat(tokenRequests.get(0).authorization()).isNull();
        assertThat(tokenRequests.get(0).form())
                .containsEntry("client_id", "agent")
                .containsEntry("client_secret", "secret");
    }

    @Test
    void refuses_a_server_that_does_not_offer_the_client_credentials_grant() {
        publish("/auth", null, ",\"grant_types_supported\":[\"authorization_code\"]");
        OAuth2ClientCredentialsAuthProvider provider = provider().build();

        assertThatThrownBy(() -> provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm"))))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("does not support the client_credentials grant");
        assertThat(provider.tokenEndpoint()).isNull();
    }

    @Test
    void refuses_a_server_whose_client_authentication_methods_are_unsupported() {
        publish("/auth", null, ",\"token_endpoint_auth_methods_supported\":[\"private_key_jwt\"]");
        OAuth2ClientCredentialsAuthProvider provider = provider().build();

        assertThatThrownBy(() -> provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm"))))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("private_key_jwt");
    }

    @Test
    void discovery_failure_surfaces_from_the_challenge() {
        OAuth2ClientCredentialsAuthProvider provider = provider().build();

        assertThatThrownBy(() -> provider.onChallenge(challenge(Map.of("resource_metadata", base + "/missing"))))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("/missing returned status 404");
    }

    @Test
    void a_later_401_reuses_the_discovered_endpoint_and_only_refreshes_the_token() {
        publish("/auth", null, "");
        OAuth2ClientCredentialsAuthProvider provider = provider().build();
        McpAuthRequest request = new McpAuthRequest("POST", mcpServer, null);
        provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm")));
        provider.getAuthorization(request);
        requestedPaths.clear();

        assertThat(provider.onChallenge(
                        challenge(Map.of("resource_metadata", base + "/prm", "error", "invalid_token"))))
                .isTrue();
        provider.getAuthorization(request);

        assertThat(requestedPaths).containsExactly("/auth/token");
        assertThat(tokenRequests).hasSize(2);
    }

    @Test
    void rediscovers_when_a_challenge_points_at_different_resource_metadata() {
        publish("/auth", null, "");
        documents.put(
                "/prm2", "{\"resource\":\"" + base + "/mcp\",\"authorization_servers\":[\"" + base + "/auth2\"]}");
        documents.put(
                "/.well-known/oauth-authorization-server/auth2",
                "{\"issuer\":\"" + base + "/auth2\",\"token_endpoint\":\"" + base + "/auth2/token\"}");
        OAuth2ClientCredentialsAuthProvider provider = provider().build();
        McpAuthRequest request = new McpAuthRequest("POST", mcpServer, null);
        provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm")));
        assertThat(provider.getAuthorization(request)).isEqualTo("Bearer tok-/auth/token");

        provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm2")));

        assertThat(provider.tokenEndpoint()).isEqualTo(URI.create(base + "/auth2/token"));
        assertThat(provider.getAuthorization(request)).isEqualTo("Bearer tok-/auth2/token");
    }

    @Test
    void a_configured_token_endpoint_disables_discovery() {
        OAuth2ClientCredentialsAuthProvider provider =
                provider().tokenEndpoint(base + "/fixed/token").build();
        McpAuthRequest request = new McpAuthRequest("POST", mcpServer, null);

        assertThat(provider.getAuthorization(request)).isEqualTo("Bearer tok-/fixed/token");
        assertThat(provider.onChallenge(challenge(Map.of("resource_metadata", base + "/prm"))))
                .isTrue();
        provider.getAuthorization(request);

        assertThat(requestedPaths).containsExactly("/fixed/token", "/fixed/token");
    }
}
