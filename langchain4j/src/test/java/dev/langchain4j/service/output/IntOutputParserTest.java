package dev.langchain4j.service.output;

import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("Should parse valid input into an integer (plain or JSON)")
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
    @DisplayName("Should throw NumberFormatException('No data to parse') on empty or null inputs")
    void should_fail_on_no_data_input(String input) {
        assertThatThrownBy(() -> parser.parse(input))
                .isInstanceOf(NumberFormatException.class)
                .hasMessageContaining("No data to parse");
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
    @DisplayName("Should throw IllegalArgumentException('Argument \"value of the int output parser\" ') for invalid inputs")
    void should_fail_on_invalid_non_integer_json_input(String input) {
        assertThatThrownBy(() -> parser.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Argument \"value of the int output parser\" ");
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
                "-3.1"
        );
    }

    @Test
    @DisplayName("formatInstructions() should return 'integer number'")
    void test_formatInstructions() {
        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("integer number");
    }
}
