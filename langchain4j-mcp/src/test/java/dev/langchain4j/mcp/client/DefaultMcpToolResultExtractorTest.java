package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

public class DefaultMcpToolResultExtractorTest {

    private final DefaultMcpToolResultExtractor extractor = new DefaultMcpToolResultExtractor();

    @Test
    public void should_extract_single_text_content() {
        ArrayNode content = JsonNodeFactory.instance.arrayNode();
        content.addObject().put("type", "text").put("text", "hello");

        assertThat(extractor.extract(content, false).resultText()).isEqualTo("hello");
        assertThat(extractor.extract(content, false).isError()).isFalse();
    }

    @Test
    public void should_join_multiple_text_parts_with_newlines() {
        ArrayNode content = JsonNodeFactory.instance.arrayNode();
        content.addObject().put("type", "text").put("text", "hello");
        content.addObject().put("type", "text").put("text", "world");

        assertThat(extractor.extract(content, false).resultText()).isEqualTo("hello\nworld");
    }

    @Test
    public void should_reject_unsupported_content_types() {
        ArrayNode content = JsonNodeFactory.instance.arrayNode();
        content.addObject().put("type", "image");

        assertThatThrownBy(() -> extractor.extract(content, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unsupported content type: image");
    }
}
