package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.*;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class McpResourcesAsToolsStdioIT extends McpResourcesAsToolsTestBase {

    @BeforeAll
    static void setup() {
        skipTestsIfJbangNotAvailable();
        McpTransport transportAlice = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(),
                        "--quiet",
                        "--fresh",
                        "run",
                        "-Dquarkus.http.port=8182",
                        getPathToScript("resources_alice_mcp_server.java")))
                .logEvents(true)
                .build();
        mcpClientAlice = new DefaultMcpClient.Builder()
                .transport(transportAlice)
                .key("alice")
                .protocolVersion("2026-07-28")
                .build();
        McpTransport transportBob = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(),
                        "--quiet",
                        "--fresh",
                        "run",
                        "-Dquarkus.http.port=8183",
                        getPathToScript("resources_bob_mcp_server.java")))
                .logEvents(true)
                .build();
        mcpClientBob = new DefaultMcpClient.Builder()
                .transport(transportBob)
                .key("bob")
                .protocolVersion("2026-07-28")
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (mcpClientAlice != null) {
            mcpClientAlice.close();
        }
        if (mcpClientBob != null) {
            mcpClientBob.close();
        }
    }
}
