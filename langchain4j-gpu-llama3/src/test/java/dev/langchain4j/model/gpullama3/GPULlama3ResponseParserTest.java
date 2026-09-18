package dev.langchain4j.model.gpullama3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.gpullama3.GPULlama3ResponseParser.ParsedResponse;
import org.junit.jupiter.api.Test;

class GPULlama3ResponseParserTest {

    @Test
    void should_preserve_whitespace_in_response_after_thinking_block() {
        String raw = "<think>reasoning</think>line1\nline2\n\n  indented";

        ParsedResponse parsed = GPULlama3ResponseParser.parseResponse(raw);

        // Internal newlines and indentation of the response body must be preserved.
        assertThat(parsed.getActualResponse()).isEqualTo("line1\nline2\n\n  indented");
    }

    @Test
    void should_trim_leading_and_trailing_whitespace_around_response() {
        String raw = "<think>r</think>\n  hello world  \n";

        ParsedResponse parsed = GPULlama3ResponseParser.parseResponse(raw);

        // Leading/trailing whitespace is trimmed, internal spacing kept.
        assertThat(parsed.getActualResponse()).isEqualTo("hello world");
    }

    @Test
    void should_not_alter_response_when_no_thinking_block_present() {
        String raw = "plain\nresponse";

        ParsedResponse parsed = GPULlama3ResponseParser.parseResponse(raw);

        assertThat(parsed.getActualResponse()).isEqualTo("plain\nresponse");
        assertThat(parsed.getThinkingContent()).isNull();
        assertThat(parsed.hasThinking()).isFalse();
    }

    @Test
    void should_extract_thinking_content_including_tags() {
        String raw = "<think>step by step</think>answer";

        ParsedResponse parsed = GPULlama3ResponseParser.parseResponse(raw);

        assertThat(parsed.getThinkingContent()).isEqualTo("<think>step by step</think>");
        assertThat(parsed.getActualResponse()).isEqualTo("answer");
        assertThat(parsed.hasThinking()).isTrue();
    }

    @Test
    void should_throw_when_raw_response_is_null() {
        assertThatThrownBy(() -> GPULlama3ResponseParser.parseResponse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
