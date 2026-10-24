package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultMcpToolResultConverterTest {

    private final DefaultMcpToolResultConverter extractor = new DefaultMcpToolResultConverter();

    @Test
    void extracts_single_text_item() {
        ToolExecutionResult result = extractor.convert(List.of(Map.of("type", "text", "text", "hello")), false);

        assertThat(result.resultText()).isEqualTo("hello");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void joins_multiple_text_items_with_newlines() {
        ToolExecutionResult result = extractor.convert(
                List.of(Map.of("type", "text", "text", "a"), Map.of("type", "text", "text", "b")), false);

        assertThat(result.resultText()).isEqualTo("a\nb");
    }

    @Test
    void propagates_application_level_error_flag() {
        ToolExecutionResult result = extractor.convert(List.of(Map.of("type", "text", "text", "boom")), true);

        assertThat(result.isError()).isTrue();
    }

    @Test
    void rejects_unsupported_content_type() {
        assertThatThrownBy(() -> extractor.convert(List.of(Map.of("type", "audio", "data", "xxx")), false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unsupported content type");
    }

    @Test
    void extracts_image_as_native_content() {
        ToolExecutionResult result = extractor.convert(
                List.of(Map.of("type", "image", "data", "iVBORw0KGgo=", "mimeType", "image/png")), false);

        assertThat(result.resultContents()).containsExactly(ImageContent.from("iVBORw0KGgo=", "image/png"));
        assertThatThrownBy(result::resultText).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejects_malformed_image_instead_of_returning_placeholder_text() {
        for (Map<String, Object> image : List.<Map<String, Object>>of(
                Map.of("type", "image", "data", "xxx"),
                Map.of("type", "image", "mimeType", "image/png"),
                Map.of("type", "image", "data", 42, "mimeType", "image/png"),
                Map.of("type", "image", "data", "xxx", "mimeType", 42),
                Map.of("type", "image", "data", "", "mimeType", "image/png"))) {
            assertThatThrownBy(() -> extractor.convert(List.of(image), false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void extracts_resource_text_content() {
        ToolExecutionResult result = extractor.convert(
                List.of(Map.of("type", "resource", "resource", Map.of("text", "resource text"))), false);

        assertThat(result.resultText()).isEqualTo("resource text");
    }

    @Test
    void is_a_functional_interface_so_it_can_be_a_lambda() {
        McpToolResultConverter lambda = (content, isError) -> ToolExecutionResult.builder()
                .resultText("items=" + content.size())
                .isError(isError)
                .build();

        assertThat(lambda.convert(List.of(Map.of("type", "text", "text", "x")), false)
                        .resultText())
                .isEqualTo("items=1");
    }

    @Test
    void rejects_binary_resources_instead_of_sending_base64_as_text() {
        assertThatThrownBy(() -> extractor.convert(
                        List.of(Map.of(
                                "type",
                                "resource",
                                "resource",
                                Map.of(
                                        "uri",
                                        "file:///report.pdf",
                                        "blob",
                                        "JVBERg==",
                                        "mimeType",
                                        "application/pdf"))),
                        true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("binary resource");
    }

    @Test
    void rejects_binary_resource_without_mime_type() {
        assertThatThrownBy(() -> extractor.convert(
                        List.of(Map.of("type", "resource", "resource", Map.of("uri", "file:///data", "blob", "AA=="))),
                        false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("binary resource");
    }

    @Test
    void rejects_empty_binary_resource_explicitly() {
        assertThatThrownBy(() -> extractor.convert(
                        List.of(Map.of("type", "resource", "resource", Map.of("uri", "file:///empty", "blob", ""))),
                        false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("binary resource");
    }

    @Test
    void preserves_empty_text_resource() {
        ToolExecutionResult result = extractor.convert(
                List.of(Map.of("type", "resource", "resource", Map.of("uri", "file:///empty.txt", "text", ""))), false);

        assertThat(result.resultText()).isEmpty();
    }

    @Test
    void rejects_resource_without_text_or_blob() {
        for (Map<String, Object> contentItem : List.<Map<String, Object>>of(
                Map.of("type", "resource"),
                Map.of("type", "resource", "resource", "invalid"),
                Map.of("type", "resource", "resource", Map.of("uri", "file:///missing")))) {
            assertThatThrownBy(() -> extractor.convert(List.of(contentItem), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("text or blob");
        }
    }

    @Test
    void joins_mixed_content_in_server_order() {
        ToolExecutionResult result = extractor.convert(
                List.of(
                        Map.of("type", "text", "text", "first"),
                        Map.of("type", "image", "mimeType", "image/png", "data", "iVBORw0KGgo="),
                        Map.of("type", "resource", "resource", Map.of("text", "last"))),
                false);

        assertThat(result.resultContents())
                .containsExactly(
                        TextContent.from("first"),
                        ImageContent.from("iVBORw0KGgo=", "image/png"),
                        TextContent.from("last"));
    }

    @Test
    void joins_plain_and_resource_text_for_text_only_models() {
        ToolExecutionResult result = extractor.convert(
                List.of(
                        Map.of("type", "text", "text", "first"),
                        Map.of("type", "resource", "resource", Map.of("text", "second")),
                        Map.of("type", "text", "text", "")),
                false);
        assertThat(result.resultText()).isEqualTo("first\nsecond\n");
        assertThat(result.resultContents()).containsExactly(TextContent.from("first\nsecond\n"));
    }

    @Test
    void retains_empty_content_as_an_empty_text_result() {
        assertThat(extractor.convert(List.of(), false).resultText()).isEmpty();
    }
}
