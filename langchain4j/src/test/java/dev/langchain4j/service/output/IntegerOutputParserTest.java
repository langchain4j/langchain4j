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

class IntegerOutputParserTest {

    private final IntegerOutputParser parser = new IntegerOutputParser();

    @ParameterizedTest
    @MethodSource
    void should_parse_valid_input(String input, Integer expected) {

        // when
        Integer actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_valid_input() {
        return Stream.of(

                // Plain text
                Arguments.of("42", 42),
                Arguments.of("   42   ", 42),
                Arguments.of("-5", -5),
                Arguments.of("0", 0),
                Arguments.of("123.0", 123),
                Arguments.of(String.valueOf(Integer.MAX_VALUE), Integer.MAX_VALUE),
                Arguments.of(String.valueOf(Integer.MIN_VALUE), Integer.MIN_VALUE),

                // JSON
                Arguments.of("{\"value\": 123}", 123),
                Arguments.of("{\"value\": \"-7\"}", -7),
                Arguments.of("   {\"value\": 77}   ", 77),
                Arguments.of("{\"value\":\"1e3\"}", 1000)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}", "{\"value\": null}", "{\"value\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Integer");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"value\":\"xyz\"}",
            "{\"value\": 3.14}",
            "{\"foo\":\"2.71\"}",
            "abc",
            "42abc",
            "4.5",
            "-3.1",
            "2147483648", // Integer.MAX_VALUE + 1
            "-2147483649" // Integer.MIN_VALUE - 1
    })
    void should_fail_to_parse_invalid_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Integer");
    }

    @Test
    void test_formatInstructions() {
        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("integer number");
    }
}
