package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    @Test
    void should_parse_collection_from_json_with_values_field() {
        // given
        String text = "{\"values\": [\"item1\", \"item2\", \"item3\"]}";
        Function<String, String> parser = Function.identity();

        // when
        List<String> result = ParsingUtils.parseAsStringOrJson(text, parser, ArrayList::new, "String");

        // then
        assertThat(result).containsExactly("item1", "item2", "item3");
    }

    @Test
    void should_parse_collection_from_multiline_string_when_not_json() {
        // given
        String text = "line1\nline2\n\nline3\n  ";
        Function<String, String> parser = Function.identity();

        // when
        List<String> result = ParsingUtils.parseAsStringOrJson(text, parser, ArrayList::new, "String");

        // then
        assertThat(result).containsExactly("line1", "line2", "line3");
    }

    @Test
    void should_throw_when_null_text_for_collection() {
        // given
        String text = null;
        Function<String, String> parser = Function.identity();

        // when/then
        assertThatThrownBy(() -> ParsingUtils.parseAsStringOrJson(text, parser, ArrayList::new, "String"))
                .isInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void should_throw_when_json_missing_values_field_for_collection() {
        // given
        String text = "{\"value\": \"single\"}";
        Function<String, String> parser = Function.identity();

        // when/then
        assertThatThrownBy(() -> ParsingUtils.parseAsStringOrJson(text, parser, ArrayList::new, "String"))
                .isInstanceOf(OutputParsingException.class);
    }

    @Test
    void should_throw_when_values_field_is_not_collection() {
        // given
        String text = "{\"values\": \"not a collection\"}";
        Function<String, String> parser = Function.identity();

        // when/then
        assertThatThrownBy(() -> ParsingUtils.parseAsStringOrJson(text, parser, ArrayList::new, "String"))
                .isInstanceOf(OutputParsingException.class);
    }

    @Test
    void should_handle_parser_exception_in_collection_parsing() {
        // given
        String text = "item1\nitem2";
        Function<String, Integer> parser = s -> {
            throw new IllegalArgumentException("Cannot parse: " + s);
        };

        // when/then
        assertThatThrownBy(() -> ParsingUtils.parseAsStringOrJson(text, parser, ArrayList::new, "Integer"))
                .isInstanceOf(OutputParsingException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_return_empty_collection_for_empty_multiline_string() {
        // given
        String text = "\n\n  \n  ";
        Function<String, String> parser = Function.identity();

        // when
        List<String> result = ParsingUtils.parseAsStringOrJson(text, parser, ArrayList::new, "String");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void should_use_custom_collection_supplier() {
        // given
        String text = "item1\nitem2";
        Function<String, String> parser = String::toUpperCase;

        // when
        Set<String> result = ParsingUtils.parseAsStringOrJson(text, parser, LinkedHashSet::new, "String");

        // then
        assertThat(result).isInstanceOf(LinkedHashSet.class).containsExactly("ITEM1", "ITEM2");
    }

    @Test
    void should_throw_for_null_or_blank_text_in_single_parsing() {
        // given
        Function<String, String> parser = Function.identity();

        // when/then
        assertThatThrownBy(() -> ParsingUtils.parseAsStringOrJson(null, parser, String.class))
                .isInstanceOf(OutputParsingException.class);

        assertThatThrownBy(() -> ParsingUtils.parseAsStringOrJson("", parser, String.class))
                .isInstanceOf(OutputParsingException.class);

        assertThatThrownBy(() -> ParsingUtils.parseAsStringOrJson("  ", parser, String.class))
                .isInstanceOf(OutputParsingException.class);
    }

    @Test
    void should_throw_for_empty_json_object() {
        // given
        String text = "{}";
        Function<String, String> parser = Function.identity();

        // when/then
        assertThatThrownBy(() -> ParsingUtils.parseAsStringOrJson(text, parser, String.class))
                .isInstanceOf(OutputParsingException.class);
    }

    @Test
    void should_handle_json_with_whitespace_prefix() {
        // given
        String text = "   {\"value\": \"test\"}";
        Function<String, String> parser = Function.identity();

        // when
        String result = ParsingUtils.parseAsStringOrJson(text, parser, String.class);

        // then
        assertThat(result).isEqualTo("test");
    }

    @Test
    void should_parse_numeric_values_from_json() {
        // given
        String text = "{\"value\": 1}";
        Function<String, Integer> parser = Integer::valueOf;

        // when
        Integer result = ParsingUtils.parseAsStringOrJson(text, parser, Integer.class);

        // then
        assertThat(result).isEqualTo(1);
    }
}
