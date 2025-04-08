package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntOutputParserTest {

    private final IntOutputParser parser = new IntOutputParser();

    @ParameterizedTest
    @MethodSource("validInputProvider")
    void should_parse_valid_input(String input, Integer expected) {

        // when
        Integer actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> validInputProvider() {
        return Stream.of(
                // Plain integers
                Arguments.of("42", 42),
                Arguments.of("   42   ", 42),
                Arguments.of("-5", -5),
                Arguments.of("0", 0),
                Arguments.of("123.0", 123),
                Arguments.of(String.valueOf(Integer.MAX_VALUE), Integer.MAX_VALUE),
                Arguments.of(String.valueOf(Integer.MIN_VALUE), Integer.MIN_VALUE),

                // JSON with "value" key
                Arguments.of("{\"value\": 123}", 123),
                Arguments.of("{\"value\": \"-7\"}", -7),

                // JSON fallback to first property
                Arguments.of("{\"foo\": 100}", 100),
                Arguments.of("{\"bar\":\"250\"}", 250),

                // Surrounded by whitespace
                Arguments.of("   {\"value\": 77}   ", 77),
                Arguments.of("{\"value\":\"1e3\"}", 1000)
        );
    }

    @ParameterizedTest
    @MethodSource("noDataInputProvider")
    void should_fail_on_no_data_input(String input) {
        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Integer");
    }

    static Stream<String> noDataInputProvider() {
        return Stream.of(
                // Null or blank
                null,
                "",
                "   ",

                // Empty JSON or null value
                "{}",
                "{\"value\": null}"
        );
    }

    @ParameterizedTest
    @MethodSource("invalidInputProvider")
    void should_fail_on_invalid_non_integer_json_input(String input) {
        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Integer");
    }

    static Stream<String> invalidInputProvider() {
        return Stream.of(
                // JSON with non-integer or decimal
                "{\"value\":\"xyz\"}",
                "{\"value\": 3.14}",
                "{\"foo\":\"2.71\"}",

                // Non-numeric text
                "abc",
                "42abc",
                "4.5",
                "-3.1",

                // out of bounds
                String.valueOf(((long) Integer.MAX_VALUE) + 1),
                String.valueOf(((long) Integer.MIN_VALUE) - 1)
        );
    }

    @Test
    void test_formatInstructions() {
        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("integer number");
    }
}
