package dev.langchain4j.mcp.client.integration.registryclient;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.registryclient.DefaultMcpRegistryClient;
import dev.langchain4j.mcp.registryclient.model.McpGetServerResponse;
import dev.langchain4j.mcp.registryclient.model.McpRegistryHealth;
import dev.langchain4j.mcp.registryclient.model.McpRegistryPong;
import dev.langchain4j.mcp.registryclient.model.McpServerList;
import dev.langchain4j.mcp.registryclient.model.McpServerListRequest;
import java.time.LocalDateTime;
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
        McpGetServerResponse server = response.getServers().stream()
                .filter(s -> s.getServer().getName().equals("io.github.bytedance/mcp-server-filesystem"))
                .findFirst()
                .orElseThrow();
        verifyMetadataOfServer(server);
    }

    @Test
    public void testListServersUpdatedSince() {
        // NOTE: the DateTimes are evaluated in UTC
        LocalDateTime updatedSince = LocalDateTime.now().minusDays(30);
        McpServerList response = client.listServers(McpServerListRequest.builder()
                .updatedSince(updatedSince)
                .version("latest")
                .build());
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
    public void testGetAllVersionsOfServer() {
        McpServerList server = client.getAllVersionsOfServer("io.github.bytedance/mcp-server-filesystem");
        assertThat(server.getServers()).hasSizeGreaterThan(0);
        for (McpGetServerResponse s : server.getServers()) {
            verifyMetadataOfServer(s);
        }
    }

    @Test
    public void testGetSpecificServerVersion() {
        McpGetServerResponse response =
                client.getSpecificServerVersion("io.github.bytedance/mcp-server-filesystem", "latest");
        assertThat(response.getServer().getVersion()).isNotBlank();
        verifyMetadataOfServer(response);
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

    private void verifyMetadataOfServer(McpGetServerResponse response) {
        // Let's not depend too much on the exact metadata of the servers because it may change...
        // For now, just a very generic check to make sure no important field is missing.
        // When we migrate to our own subregistry (or a mock thereof), we can verify the metadata deeper.
        assertThat(response.getServer().getDescription()).isNotBlank();
        assertThat(response.getServer().getVersion()).isNotBlank();
        assertThat(response.getServer().getRepository().getUrl()).isNotBlank();
        assertThat(response.getServer().getRepository().getSource()).isNotBlank();
        assertThat(response.getServer().getRepository().getSubfolder()).isNotBlank();
        assertThat(response.getServer().getPackages()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(response.getServer().getPackages().get(0).getRegistryType()).isNotBlank();
        assertThat(response.getServer().getPackages().get(0).getRegistryBaseUrl())
                .isNotBlank();
        assertThat(response.getServer().getPackages().get(0).getIdentifier()).isNotBlank();
        assertThat(response.getServer().getPackages().get(0).getVersion()).isNotBlank();
        assertThat(response.getServer().getPackages().get(0).getTransport()).isNotNull();
        assertThat(response.getServer().getPackages().get(0).getTransport().getType())
                .isNotBlank();
        assertThat(response.getMeta().getOfficial().getPublishedAt()).isNotNull();
        assertThat(response.getMeta().getOfficial().getUpdatedAt()).isNotNull();
        assertThat(response.getMeta().getOfficial().getStatus()).isNotBlank();
    }
}
