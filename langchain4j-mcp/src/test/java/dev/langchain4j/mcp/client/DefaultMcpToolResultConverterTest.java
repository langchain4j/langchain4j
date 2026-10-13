package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void extracts_image_as_data_uri() {
        ToolExecutionResult result = extractor.convert(
                List.of(Map.of("type", "image", "data", "iVBORw0KGgo=", "mimeType", "image/png")), false);

        assertThat(result.resultText()).isEqualTo("data:image/png;base64,iVBORw0KGgo=");
    }

    @Test
    void handles_image_with_missing_fields() {
        ToolExecutionResult result = extractor.convert(List.of(Map.of("type", "image", "data", "xxx")), false);

        assertThat(result.resultText()).isEqualTo("[image: missing data or mimeType]");
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
    void extracts_resource_blob_as_data_uri_and_preserves_error_flag() {
        ToolExecutionResult result = extractor.convert(
                List.of(Map.of(
                        "type",
                        "resource",
                        "resource",
                        Map.of("uri", "file:///report.pdf", "blob", "JVBERg==", "mimeType", "application/pdf"))),
                true);

        assertThat(result.resultText()).isEqualTo("data:application/pdf;base64,JVBERg==");
        assertThat(result.isError()).isTrue();
    }

    @Test
    void defaults_binary_resource_mime_type_when_omitted() {
        ToolExecutionResult result = extractor.convert(
                List.of(Map.of("type", "resource", "resource", Map.of("uri", "file:///data", "blob", "AA=="))), false);

        assertThat(result.resultText()).isEqualTo("data:application/octet-stream;base64,AA==");
    }

    @Test
    void preserves_empty_binary_resource() {
        ToolExecutionResult result = extractor.convert(
                List.of(Map.of("type", "resource", "resource", Map.of("uri", "file:///empty", "blob", ""))), false);

        assertThat(result.resultText()).isEqualTo("data:application/octet-stream;base64,");
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
                        Map.of("type", "resource", "resource", Map.of("uri", "file:///data", "blob", "AA=="))),
                false);

        assertThat(result.resultText())
                .isEqualTo("first\ndata:image/png;base64,iVBORw0KGgo=\ndata:application/octet-stream;base64,AA==");
    }
}
