package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FloatOutputParserTest {

    private final FloatOutputParser parser = new FloatOutputParser();

    @ParameterizedTest
    @MethodSource("validInputProvider")
    void should_parse_valid_input(String input, Float expected) {

        // when
        Float actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> validInputProvider() {
        return Stream.of(
                // Plain floats
                Arguments.of("3.14", 3.14f),
                Arguments.of("   3.14   ", 3.14f),
                Arguments.of("-2.718", -2.718f),
                Arguments.of("0", 0.0f),
                Arguments.of("42", 42.0f),
                Arguments.of(String.valueOf(Float.MAX_VALUE), Float.MAX_VALUE),
                Arguments.of("1e309", Float.POSITIVE_INFINITY),
                Arguments.of(String.valueOf(Float.MIN_VALUE), Float.MIN_VALUE),
                Arguments.of("-1e309", Float.NEGATIVE_INFINITY),

                // JSON with "value" key
                Arguments.of("{\"value\": 1.23}", 1.23f),
                Arguments.of("{\"value\": \"-4.56\"}", -4.56f),
                Arguments.of("{\"value\": 0}", 0.0f),
                Arguments.of("{\"value\": \"42\"}", 42.0f),

                // Surrounded by whitespace
                Arguments.of("   {\"value\": 77.99}   ", 77.99f),

                // JSON fallback to first property
                Arguments.of("{\"foo\": 99.001}", 99.001f),
                Arguments.of("{\"bar\": \"-100.5\"}", -100.5f)
        );
    }

    @ParameterizedTest
    @MethodSource("noDataInputProvider")
    void should_fail_on_no_data_input(String input) {
        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Float");
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
    void should_fail_on_invalid_input(String input) {
        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Float");
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
    void test_formatInstructions() {
        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("floating point number");
    }
}
