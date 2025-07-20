package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoubleOutputParserTest {

    private final DoubleOutputParser parser = new DoubleOutputParser();

    @ParameterizedTest
    @MethodSource
    void should_parse_valid_input(String input, Double expected) {

        // when
        Double actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_valid_input() {
        return Stream.of(

                // Plain text
                Arguments.of("3.14", 3.14),
                Arguments.of("   3.14   ", 3.14),
                Arguments.of("-2.718", -2.718),
                Arguments.of("0", 0.0),
                Arguments.of("42", 42.0),
                Arguments.of(String.valueOf(Double.MAX_VALUE), Double.MAX_VALUE),
                Arguments.of("1e309", Double.POSITIVE_INFINITY),
                Arguments.of(String.valueOf(Double.MIN_VALUE), Double.MIN_VALUE),
                Arguments.of("-1e309", Double.NEGATIVE_INFINITY),

                // JSON
                Arguments.of("{\"value\": 1.23}", 1.23),
                Arguments.of("{\"value\": \"-4.56\"}", -4.56),
                Arguments.of("{\"value\": 0}", 0.0),
                Arguments.of("{\"value\": \"42\"}", 42.0),
                Arguments.of("   {\"value\": 77.99}   ", 77.99)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}", "{\"value\": null}", "{\"value\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Double");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "4.5.6", "123,4", "{\"value\": \"abc\"}", "{\"value\": \"4.5.6\"}"})
    void should_fail_to_parse_invalid_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Double");
    }

    @Test
    void test_formatInstructions() {
        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("floating point number");
    }
}
