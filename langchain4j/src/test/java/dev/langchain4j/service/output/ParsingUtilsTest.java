package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ParsingUtilsTest {

    @Test
    void should_dump_problematic_text_in_base64() {

        // given
        String text = "invalid json";

        // when
        OutputParsingException e = ParsingUtils.outputParsingException(text, Object.class);

        // then
        assertThat(e).hasMessageContaining("aW52YWxpZCBqc29u");
    }

    @Test
    void should_dump_problematic_text_in_base64_null() {

        // given
        String text = null;

        // when
        OutputParsingException e = ParsingUtils.outputParsingException(text, Object.class);

        // then
        assertThat(e).hasMessageContaining("null");
    }

    @Test
    void should_include_target_class_in_exception_message() {
        // given
        String text = "invalid";
        Class<?> targetClass = String.class;

        // when
        OutputParsingException e = ParsingUtils.outputParsingException(text, targetClass);

        // then
        assertThat(e).hasMessageContaining("String");
    }

    @Test
    void should_parse_simple_json_with_value_field() {
        // given
        String text = "{\"value\": \"test content\"}";
        Function<String, String> parser = Function.identity();

        // when
        String result = ParsingUtils.parseAsStringOrJson(text, parser, String.class);

        // then
        assertThat(result).isEqualTo("test content");
    }

    @Test
    void should_parse_plain_string_when_not_json() {
        // given
        String text = "plain text";
        Function<String, String> parser = Function.identity();

        // when
        String result = ParsingUtils.parseAsStringOrJson(text, parser, String.class);

        // then
        assertThat(result).isEqualTo("plain text");
    }

    @Test
    void should_throw_when_json_missing_value_field() {
        // given
        String text = "{\"other\": \"content\"}";
        Function<String, String> parser = Function.identity();

        // when/then
        assertThatThrownBy(() -> ParsingUtils.parseAsStringOrJson(text, parser, String.class))
                .isInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void should_handle_parser_exception() {
        // given
        String text = "123";
        Function<String, Integer> parser = s -> {
            throw new IllegalArgumentException("Cannot parse");
        };

        // when/then
        assertThatThrownBy(() -> ParsingUtils.parseAsStringOrJson(text, parser, Integer.class))
                .isInstanceOf(OutputParsingException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
