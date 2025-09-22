package dev.langchain4j.mcp.client.integration.registryclient;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.registryclient.DefaultMcpRegistryClient;
import dev.langchain4j.mcp.registryclient.model.McpRegistryHealth;
import dev.langchain4j.mcp.registryclient.model.McpRegistryPong;
import dev.langchain4j.mcp.registryclient.model.McpServer;
import dev.langchain4j.mcp.registryclient.model.McpServerList;
import dev.langchain4j.mcp.registryclient.model.McpServerListRequest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: this test runs against the official MCP registry at https://registry.modelcontextprotocol.io/
 * We might want to stop depending on it and instead run our own subregistry when we have a suitable SDK
 * available to do that for testing. Another option is to introduce a mock that substitutes it.
 */
public class McpRegistryClientIT {

    private static final Logger log = LoggerFactory.getLogger(McpRegistryClientIT.class);

    static DefaultMcpRegistryClient client;

    @BeforeAll
    public static void prepare() {
        client = DefaultMcpRegistryClient.builder()
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Test
    public void testListServers() {
        McpServerList response = client.listServers(
                McpServerListRequest.builder().search("mcp-server-filesystem").build());
        McpServer server = response.getServers().stream()
                .filter(s -> s.getName().equals("io.github.bytedance/mcp-server-filesystem"))
                .findFirst()
                .orElseThrow();
        verifyMetadataOfServer(server);
    }

    @Test
    public void testListServersUpdatedSince() {
        ZonedDateTime updatedSince = ZonedDateTime.now(ZoneId.systemDefault()).minusDays(30);
        McpServerList response = client.listServers(
                McpServerListRequest.builder().updatedSince(updatedSince).build());
        assertThat(response.getServers()).hasSizeGreaterThanOrEqualTo(1);
        // assert that all returned servers have been updated since 30 days ago
        assertThat(response.getServers())
                .allMatch(s -> s.getMeta().getOfficial().getUpdatedAt().isAfter(updatedSince));
    }

    @Test
    public void testListServersLimit() {
        McpServerList response =
                client.listServers(McpServerListRequest.builder().limit(7L).build());
        assertThat(response.getServers()).hasSize(7);
    }

    @Test
    public void testGetServer() {
        McpServer server = client.getServerDetails(
                "86863c74-2ae5-4430-8880-5474e7ae2155"); // this is io.github.bytedance/mcp-server-filesystem
        verifyMetadataOfServer(server);
    }

    @Test
    public void testHealth() {
        DefaultMcpRegistryClient client = DefaultMcpRegistryClient.builder().build();
        McpRegistryHealth health = client.healthCheck();
        assertThat(health.getStatus()).isEqualTo("ok");
    }

    @Test
    public void testPing() {
        DefaultMcpRegistryClient client = DefaultMcpRegistryClient.builder().build();
        McpRegistryPong pong = client.ping();
        assertThat(pong.pong()).isTrue();
    }

    private void verifyMetadataOfServer(McpServer server) {
        // Let's not depend too much on the exact metadata of the servers because it may change...
        // For now, just a very generic check to make sure no important field is missing.
        // When we migrate to our own subregistry (or a mock thereof), we can verify the metadata deeper.
        assertThat(server.getDescription()).isNotBlank();
        assertThat(server.getStatus()).isNotBlank();
        assertThat(server.getVersion()).isNotBlank();
        assertThat(server.getRepository().getUrl()).isNotBlank();
        assertThat(server.getRepository().getSource()).isNotBlank();
        assertThat(server.getRepository().getSubfolder()).isNotBlank();
        assertThat(server.getPackages()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(server.getPackages().get(0).getRegistryType()).isNotBlank();
        assertThat(server.getPackages().get(0).getRegistryBaseUrl()).isNotBlank();
        assertThat(server.getPackages().get(0).getIdentifier()).isNotBlank();
        assertThat(server.getPackages().get(0).getVersion()).isNotBlank();
        assertThat(server.getPackages().get(0).getTransport()).isNotNull();
        assertThat(server.getPackages().get(0).getTransport().getType()).isNotBlank();
        assertThat(server.getMeta().getOfficial().getServerId()).isNotBlank();
        assertThat(server.getMeta().getOfficial().getPublishedAt()).isNotNull();
        assertThat(server.getMeta().getOfficial().getUpdatedAt()).isNotNull();
    }
}
