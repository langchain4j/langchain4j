package dev.langchain4j.mcp.client.integration;

import static dev.langchain4j.mcp.client.integration.McpServerHelper.getJBangCommand;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.getPathToScript;
import static dev.langchain4j.mcp.client.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpToolResultExtractor;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class McpToolResultExtractorStdioTransportIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static DefaultMcpClient client;

    @BeforeAll
    static void setup() {
        skipTestsIfJbangNotAvailable();

        McpToolResultExtractor extractor = new McpToolResultExtractor() {
            @Override
            public ToolExecutionResult extract(JsonNode content, boolean isError) {
                if (content.isEmpty()) {
                    return ToolExecutionResult.builder()
                            .isError(isError)
                            .resultText("")
                            .build();
                }

                String type = content.get(0).get("type").asText();

                if ("text".equals(type) && content.size() == 1) {
                    try {
                        Map<String, Object> map = OBJECT_MAPPER.convertValue(
                                OBJECT_MAPPER.readTree(
                                        content.get(0).get("text").asText()),
                                Map.class);
                        return ToolExecutionResult.builder()
                                .isError(isError)
                                .result(map)
                                .resultText(content.get(0).get("text").asText())
                                .build();
                    } catch (Exception ignored) {
                        return ToolExecutionResult.builder()
                                .isError(isError)
                                .resultText(content.get(0).get("text").asText())
                                .build();
                    }
                }

                if ("image".equals(type)) {
                    return ToolExecutionResult.builder()
                            .isError(isError)
                            .resultContents(List.of(ImageContent.from(
                                    content.get(0).get("data").asText(),
                                    content.get(0).get("mimeType").asText())))
                            .build();
                }

                return ToolExecutionResult.builder()
                        .isError(isError)
                        .resultContents(List.of(
                                TextContent.from(content.get(0).get("text").asText()),
                                ImageContent.from(
                                        content.get(1).get("data").asText(),
                                        content.get(1).get("mimeType").asText())))
                        .build();
            }
        };

        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(),
                        "--quiet",
                        "--fresh",
                        "run",
                        getPathToScript("tool_result_extractor_mcp_server.java")))
                .logEvents(true)
                .build();

        client = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .toolResultExtractor(extractor)
                .build();
    }

    @AfterAll
    static void teardown() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void should_extract_json_text_content_with_custom_extractor() {
        ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                .name("jsonContent")
                .arguments("{}")
                .build());

        assertThat(result.result()).isEqualTo(Map.of("value", 42, "status", "ok"));
        assertThat(result.resultText()).isEqualTo("{\"value\":42,\"status\":\"ok\"}");
    }

    @Test
    void should_extract_image_content_with_custom_extractor() {
        ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                .name("imageContent")
                .arguments("{}")
                .build());

        assertThat(result.resultContents()).hasSize(1);
        assertThat(result.resultContents().get(0)).isInstanceOf(ImageContent.class);

        ImageContent imageContent = (ImageContent) result.resultContents().get(0);
        assertThat(imageContent.image().base64Data()).isEqualTo("iVBORw0KGgo=");
        assertThat(imageContent.image().mimeType()).isEqualTo("image/png");
    }

    @Test
    void should_extract_mixed_multimodal_content_with_custom_extractor() {
        ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                .name("mixedContent")
                .arguments("{}")
                .build());

        assertThat(result.resultContents()).hasSize(2);
        assertThat(result.resultContents().get(0)).isEqualTo(TextContent.from("preview"));
        assertThat(result.resultContents().get(1)).isInstanceOf(ImageContent.class);
    }

    @Test
    void should_not_apply_custom_extractor_to_structured_content() {
        AtomicBoolean extractorCalled = new AtomicBoolean(false);

        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        getJBangCommand(),
                        "--quiet",
                        "--fresh",
                        "run",
                        getPathToScript("tool_result_extractor_mcp_server.java")))
                .logEvents(true)
                .build();

        try (DefaultMcpClient structuredContentClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(4))
                .toolResultExtractor((content, isError) -> {
                    extractorCalled.set(true);
                    return ToolExecutionResult.builder()
                            .isError(isError)
                            .resultText("wrong")
                            .build();
                })
                .build()) {

            ToolExecutionResult result = structuredContentClient.executeTool(ToolExecutionRequest.builder()
                    .name("structuredContent")
                    .arguments("{}")
                    .build());

            assertThat(extractorCalled).isFalse();
            assertThat(result.result()).isEqualTo(Map.of("value", 7, "status", "structured"));
            assertThat(result.resultText()).isEqualTo("{\"value\":7,\"status\":\"structured\"}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
