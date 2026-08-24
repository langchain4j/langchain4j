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
        assertThatThrownBy(() -> extractor.convert(List.of(Map.of("type", "image", "data", "xxx")), false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unsupported content type");
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
}
