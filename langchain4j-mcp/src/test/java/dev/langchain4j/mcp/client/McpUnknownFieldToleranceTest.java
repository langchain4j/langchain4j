package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.mcp.client.transport.McpJson;
import dev.langchain4j.mcp.protocol.McpInitializeResult;
import dev.langchain4j.mcp.protocol.McpListPromptsResult;
import dev.langchain4j.mcp.protocol.McpListResourcesResult;
import org.junit.jupiter.api.Test;

/**
 * MCP adds fields in a backwards-compatible way — 2025-06-18 gave every named object a 'title' —
 * so a server may legitimately send fields this client does not model. Reading a response must
 * ignore them rather than fail, at every level of the document.
 */
@SuppressWarnings("unchecked")
class McpUnknownFieldToleranceTest {

    @Test
    void initialize_should_tolerate_an_unknown_field_on_serverInfo() {
        String response =
                """
                {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{},
                 "serverInfo":{"name":"s","version":"1","websiteUrl":"https://example.com"}}}""";

        McpInitializeResult result = McpJson.deserialize(McpJson.parse(response), McpInitializeResult.class);

        assertThat(result.getResult().getServerInfo().getName()).isEqualTo("s");
    }

    @Test
    void listResources_should_tolerate_title_on_a_resource() {
        String response =
                """
                {"jsonrpc":"2.0","id":1,"result":{"resources":[
                 {"uri":"file:///a","name":"n","title":"T","size":12,"annotations":{"audience":["user"]}}]}}""";

        McpListResourcesResult result = McpJson.deserialize(McpJson.parse(response), McpListResourcesResult.class);

        assertThat(result.getResult().getResources()).singleElement().satisfies(r -> assertThat(r.name())
                .isEqualTo("n"));
    }

    @Test
    void listPrompts_should_tolerate_title_on_a_prompt() {
        String response =
                """
                {"jsonrpc":"2.0","id":1,"result":{"prompts":[{"name":"p","title":"T","description":"d"}]}}""";

        McpListPromptsResult result = McpJson.deserialize(McpJson.parse(response), McpListPromptsResult.class);

        assertThat(result.getResult().getPrompts()).singleElement().satisfies(p -> assertThat(p.name())
                .isEqualTo("p"));
    }

    @Test
    void listTools_should_tolerate_an_unknown_field_on_an_icon() {
        String toolsArray =
                """
                [{"name":"t","description":"d","inputSchema":{"type":"object"},
                 "icons":[{"src":"https://example.com/i.png","mimeType":"image/png","futureIconField":1}]}]""";

        assertThatCode(() -> ToolSpecificationHelper.toolSpecificationListFromMcpResponse(
                        (java.util.List<java.util.Map<String, Object>>)
                                McpJson.toMap("{\"tools\":" + toolsArray + "}").get("tools")))
                .doesNotThrowAnyException();
    }

    @Test
    void unknown_fields_should_be_tolerated_at_envelope_and_result_level_too() {
        String response =
                """
                {"jsonrpc":"2.0","id":1,"someFutureTopLevel":true,
                 "result":{"nextCursor":"c","someFutureResultField":1,"resources":[{"uri":"file:///a","name":"n"}]}}""";

        assertThatCode(() -> McpJson.deserialize(McpJson.parse(response), McpListResourcesResult.class))
                .doesNotThrowAnyException();
    }
}
