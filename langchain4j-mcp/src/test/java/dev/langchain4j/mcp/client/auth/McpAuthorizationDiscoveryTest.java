package dev.langchain4j.mcp.client.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpAuthorizationDiscoveryTest {

    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private String base;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestedPaths.add(path);
            String body = documents.get(path);
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
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private McpAuthorizationDiscovery discovery() {
        return McpAuthorizationDiscovery.builder()
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private String prm(String resource, String... issuers) {
        StringBuilder servers = new StringBuilder();
        for (String issuer : issuers) {
            servers.append(servers.length() == 0 ? "" : ",")
                    .append('"')
                    .append(issuer)
                    .append('"');
        }
        return "{\"resource\":\"" + resource + "\",\"authorization_servers\":[" + servers
                + "],\"scopes_supported\":[\"mcp:tools\"]}";
    }

    private String asMetadata(String issuer) {
        return "{\"issuer\":\"" + issuer + "\",\"token_endpoint\":\"" + issuer + "/token\","
                + "\"grant_types_supported\":[\"client_credentials\",\"authorization_code\"],"
                + "\"token_endpoint_auth_methods_supported\":[\"client_secret_basic\",\"client_secret_post\"],"
                + "\"code_challenge_methods_supported\":[\"S256\"]}";
    }

    @Test
    void follows_the_challenge_url_then_rfc8414_metadata() {
        documents.put("/custom/prm", prm(base + "/mcp", base + "/auth"));
        documents.put("/.well-known/oauth-authorization-server/auth", asMetadata(base + "/auth"));

        McpAuthorizationDiscovery.Result result = discovery().discover(URI.create(base + "/mcp"), base + "/custom/prm");

        assertThat(result.resourceMetadataUrl()).isEqualTo(URI.create(base + "/custom/prm"));
        assertThat(result.resource().resource()).isEqualTo(base + "/mcp");
        assertThat(result.resource().scopesSupported()).containsExactly("mcp:tools");
        assertThat(result.authorizationServer().issuer()).isEqualTo(base + "/auth");
        assertThat(result.authorizationServer().tokenEndpoint()).isEqualTo(base + "/auth/token");
        assertThat(result.authorizationServer().supportsGrantType("client_credentials"))
                .isTrue();
        assertThat(result.authorizationServer().codeChallengeMethodsSupported()).containsExactly("S256");
        assertThat(requestedPaths).containsExactly("/custom/prm", "/.well-known/oauth-authorization-server/auth");
    }

    @Test
    void falls_back_to_the_path_aware_well_known_uri_and_then_to_the_root() {
        documents.put("/.well-known/oauth-protected-resource", prm(base, base));
        documents.put("/.well-known/oauth-authorization-server", asMetadata(base));

        McpAuthorizationDiscovery.Result result = discovery().discover(URI.create(base + "/public/mcp"), null);

        assertThat(result.resource().resource()).isEqualTo(base);
        assertThat(requestedPaths)
                .containsExactly(
                        "/.well-known/oauth-protected-resource/public/mcp",
                        "/.well-known/oauth-protected-resource",
                        "/.well-known/oauth-authorization-server");
    }

    @Test
    void path_aware_well_known_uri_wins_when_present() {
        documents.put("/.well-known/oauth-protected-resource/public/mcp", prm(base + "/public/mcp", base));
        documents.put("/.well-known/oauth-protected-resource", prm(base, base));
        documents.put("/.well-known/oauth-authorization-server", asMetadata(base));

        McpAuthorizationDiscovery.Result result = discovery().discover(URI.create(base + "/public/mcp/"), null);

        assertThat(result.resource().resource()).isEqualTo(base + "/public/mcp");
        assertThat(requestedPaths).doesNotContain("/.well-known/oauth-protected-resource");
    }

    @Test
    void issuer_with_a_path_is_tried_at_three_well_known_uris_in_order() {
        documents.put("/prm", prm(base + "/mcp", base + "/realms/mcp"));
        documents.put("/realms/mcp/.well-known/openid-configuration", asMetadata(base + "/realms/mcp"));

        McpAuthorizationDiscovery.Result result = discovery().discover(URI.create(base + "/mcp"), base + "/prm");

        assertThat(result.authorizationServer().tokenEndpoint()).isEqualTo(base + "/realms/mcp/token");
        assertThat(requestedPaths)
                .containsExactly(
                        "/prm",
                        "/.well-known/oauth-authorization-server/realms/mcp",
                        "/.well-known/openid-configuration/realms/mcp",
                        "/realms/mcp/.well-known/openid-configuration");
    }

    @Test
    void issuer_without_a_path_is_tried_at_two_well_known_uris_in_order() {
        documents.put("/prm", prm(base + "/mcp", base));
        documents.put("/.well-known/openid-configuration", asMetadata(base));

        discovery().discover(URI.create(base + "/mcp"), base + "/prm");

        assertThat(requestedPaths)
                .containsExactly(
                        "/prm", "/.well-known/oauth-authorization-server", "/.well-known/openid-configuration");
    }

    @Test
    void candidate_uris_follow_the_specification_examples() {
        assertThat(McpAuthorizationDiscovery.authorizationServerMetadataCandidates(
                        URI.create("https://auth.example.com/tenant1")))
                .containsExactly(
                        URI.create("https://auth.example.com/.well-known/oauth-authorization-server/tenant1"),
                        URI.create("https://auth.example.com/.well-known/openid-configuration/tenant1"),
                        URI.create("https://auth.example.com/tenant1/.well-known/openid-configuration"));
        assertThat(McpAuthorizationDiscovery.authorizationServerMetadataCandidates(
                        URI.create("https://auth.example.com/")))
                .containsExactly(
                        URI.create("https://auth.example.com/.well-known/oauth-authorization-server"),
                        URI.create("https://auth.example.com/.well-known/openid-configuration"));
        assertThat(McpAuthorizationDiscovery.resourceMetadataCandidates(
                        URI.create("https://example.com/public/mcp"), null))
                .containsExactly(
                        URI.create("https://example.com/.well-known/oauth-protected-resource/public/mcp"),
                        URI.create("https://example.com/.well-known/oauth-protected-resource"));
        assertThat(McpAuthorizationDiscovery.resourceMetadataCandidates(URI.create("https://example.com"), null))
                .containsExactly(URI.create("https://example.com/.well-known/oauth-protected-resource"));
        assertThat(McpAuthorizationDiscovery.resourceMetadataCandidates(
                        URI.create("https://example.com/mcp"), "https://example.com/meta"))
                .containsExactly(URI.create("https://example.com/meta"));
    }

    @Test
    void rejects_metadata_whose_issuer_differs_from_the_one_it_was_fetched_for() {
        documents.put("/prm", prm(base + "/mcp", base + "/auth"));
        documents.put("/.well-known/oauth-authorization-server/auth", asMetadata("https://honest.example"));

        assertThatThrownBy(() -> discovery().discover(URI.create(base + "/mcp"), base + "/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("declares issuer 'https://honest.example'")
                .hasMessageContaining("refusing");
    }

    @Test
    void rejects_resource_metadata_that_describes_another_resource() {
        documents.put("/prm", prm("https://other.example/mcp", base + "/auth"));

        assertThatThrownBy(() -> discovery().discover(URI.create(base + "/mcp"), base + "/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("is for resource 'https://other.example/mcp'");
        assertThat(requestedPaths).containsExactly("/prm");
    }

    @Test
    void rejects_resource_metadata_for_a_sibling_path() {
        documents.put("/prm", prm(base + "/other", base + "/auth"));

        assertThatThrownBy(() -> discovery().discover(URI.create(base + "/mcp"), base + "/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("is for resource");
    }

    @Test
    void accepts_a_resource_that_differs_only_in_normalization() {
        String host = "LOCALHOST:" + server.getAddress().getPort();
        documents.put("/prm", prm("http://" + host + "/mcp/", base));
        documents.put("/.well-known/oauth-authorization-server", asMetadata(base));

        McpAuthorizationDiscovery.Result result = discovery().discover(URI.create(base + "/mcp"), base + "/prm");

        assertThat(result.resource().resource()).isEqualTo("http://" + host + "/mcp/");
    }

    @Test
    void uses_the_first_authorization_server_that_resolves() {
        documents.put("/prm", prm(base + "/mcp", base + "/dead", base + "/auth"));
        documents.put("/.well-known/oauth-authorization-server/auth", asMetadata(base + "/auth"));

        McpAuthorizationDiscovery.Result result = discovery().discover(URI.create(base + "/mcp"), base + "/prm");

        assertThat(result.authorizationServer().issuer()).isEqualTo(base + "/auth");
        assertThat(requestedPaths)
                .containsExactly(
                        "/prm",
                        "/.well-known/oauth-authorization-server/dead",
                        "/.well-known/openid-configuration/dead",
                        "/dead/.well-known/openid-configuration",
                        "/.well-known/oauth-authorization-server/auth");
    }

    @Test
    void reports_every_failed_location_when_nothing_resolves() {
        documents.put("/prm", prm(base + "/mcp", base + "/auth"));

        assertThatThrownBy(() -> discovery().discover(URI.create(base + "/mcp"), base + "/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("None of the authorization servers")
                .hasMessageContaining("/.well-known/oauth-authorization-server/auth returned status 404")
                .hasMessageContaining("/auth/.well-known/openid-configuration returned status 404");
    }

    @Test
    void reports_missing_resource_metadata() {
        assertThatThrownBy(() -> discovery().discover(URI.create(base + "/mcp"), null))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("Could not find the protected resource metadata")
                .hasMessageContaining("/.well-known/oauth-protected-resource/mcp returned status 404")
                .hasMessageContaining("/.well-known/oauth-protected-resource returned status 404");
    }

    @Test
    void rejects_metadata_without_a_token_endpoint() {
        documents.put("/prm", prm(base + "/mcp", base));
        documents.put("/.well-known/oauth-authorization-server", "{\"issuer\":\"" + base + "\"}");

        assertThatThrownBy(() -> discovery().discover(URI.create(base + "/mcp"), base + "/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("has no 'token_endpoint'");
    }

    @Test
    void rejects_resource_metadata_without_authorization_servers() {
        documents.put("/prm", "{\"resource\":\"" + base + "/mcp\"}");

        assertThatThrownBy(() -> discovery().discover(URI.create(base + "/mcp"), base + "/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("lists no 'authorization_servers'");
    }

    @Test
    void refuses_plain_http_authorization_servers_off_loopback() {
        documents.put("/prm", prm(base + "/mcp", "http://auth.example.invalid"));

        assertThatThrownBy(() -> discovery().discover(URI.create(base + "/mcp"), base + "/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("http://auth.example.invalid is not served over HTTPS");
        assertThat(requestedPaths).containsExactly("/prm");
    }

    @Test
    void refuses_a_plain_http_resource_metadata_url_off_the_mcp_server_origin() {
        assertThatThrownBy(() -> discovery().discover(URI.create(base + "/mcp"), "http://mcp.example.invalid/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("is not served over HTTPS and is not on the origin of the MCP server");
        assertThat(requestedPaths).isEmpty();
    }

    @Test
    void accepts_plain_http_resource_metadata_on_the_mcp_server_origin() {
        // the MCP server is itself reached over plain HTTP on this host, so its metadata may be too
        McpAuthorizationDiscovery strict = McpAuthorizationDiscovery.builder().build();
        String host = "http://mcp.example.invalid";
        assertThat(McpAuthorizationDiscovery.resourceMetadataCandidates(URI.create(host + "/mcp"), null))
                .allSatisfy(candidate -> assertThat(candidate.toString()).startsWith(host));
        assertThatThrownBy(() -> strict.discover(URI.create(host + "/mcp"), host + "/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                // it was attempted (and failed on the network), not refused for being plain HTTP
                .hasMessageNotContaining("is not served over HTTPS");
    }

    @Test
    void rejects_oversized_documents() {
        documents.put("/prm", prm(base + "/mcp", base) + " ".repeat(200));

        McpAuthorizationDiscovery small =
                McpAuthorizationDiscovery.builder().maxResponseBytes(100).build();

        assertThatThrownBy(() -> small.discover(URI.create(base + "/mcp"), base + "/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("exceeds 100 bytes");
    }

    @Test
    void rejects_non_json_documents() {
        documents.put("/prm", "<html>login</html>");

        assertThatThrownBy(() -> discovery().discover(URI.create(base + "/mcp"), base + "/prm"))
                .isInstanceOf(McpAuthorizationDiscoveryException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void canonical_resource_lower_cases_and_strips_default_port_fragment_and_trailing_slash() {
        assertThat(McpAuthorizationDiscovery.canonicalResource(URI.create("HTTPS://MCP.Example.COM:443/mcp/#frag")))
                .isEqualTo("https://mcp.example.com/mcp");
        assertThat(McpAuthorizationDiscovery.canonicalResource(URI.create("http://host:8080/")))
                .isEqualTo("http://host:8080");
        assertThat(McpAuthorizationDiscovery.canonicalResource(URI.create("https://host/server/mcp?tenant=1")))
                .isEqualTo("https://host/server/mcp?tenant=1");
        assertThat(McpAuthorizationDiscovery.canonicalResource(URI.create("https://host")))
                .isEqualTo("https://host");
    }
}
