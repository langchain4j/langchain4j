package dev.langchain4j.service.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoubleOutputParserTest {

    private final DoubleOutputParser parser = new DoubleOutputParser();

    @ParameterizedTest
    @MethodSource("validInputProvider")
    @DisplayName("Should parse valid input (plain or JSON) into double")
    void should_parse_valid_input(String input, Double expected) {
        // when
        Double actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> validInputProvider() {
        return Stream.of(
                // Plain doubles
                Arguments.of("3.14", 3.14),
                Arguments.of("   3.14   ", 3.14),
                Arguments.of("-2.718", -2.718),
                Arguments.of("0", 0.0),
                Arguments.of("42", 42.0), // integer-like is still a valid double

                // JSON with "value" key
                Arguments.of("{\"value\": 1.23}", 1.23),
                Arguments.of("{\"value\": \"-4.56\"}", -4.56),
                Arguments.of("{\"value\": 0}", 0.0),
                Arguments.of("{\"value\": \"42\"}", 42.0),
                // Surrounded by whitespace
                Arguments.of("   {\"value\": 77.99}   ", 77.99),

                // JSON fallback to first property
                Arguments.of("{\"foo\": 99.001}", 99.001),
                Arguments.of("{\"bar\": \"-100.5\"}", -100.5)
        );
    }

    @ParameterizedTest
    @MethodSource("noDataInputProvider")
    @DisplayName("Should throw NumberFormatException('No data to parse') for empty or null inputs")
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

                // Empty JSON or JSON with null
                "{}",
                "{\"value\": null}"
        );
    }

    @ParameterizedTest
    @MethodSource("invalidInputProvider")
    @DisplayName("Should throw NumberFormatException('Could not parse double from: X') for invalid input")
    void should_fail_on_invalid_input(String input) {
        assertThatThrownBy(() -> parser.parse(input))
                .isInstanceOf(NumberFormatException.class)
                .hasMessageContaining("Could not parse double from");
    }

    static Stream<String> invalidInputProvider() {
        return Stream.of(
                // Non-numeric text
                "abc",
                "4.5.6",
                "123,4",  // using comma instead of dot

                // JSON with non-numeric or unparseable
                "{\"value\": \"abc\"}",
                "{\"value\": \"4.5.6\"}"
        );
    }

    @Test
    @DisplayName("formatInstructions() should return 'floating point number'")
    void test_formatInstructions() {
        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("floating point number");
    }
}
