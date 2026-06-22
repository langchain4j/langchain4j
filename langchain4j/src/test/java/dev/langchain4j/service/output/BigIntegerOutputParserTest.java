package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BigIntegerOutputParserTest {

    private final BigIntegerOutputParser parser = new BigIntegerOutputParser();

    @ParameterizedTest
    @MethodSource
    void should_parse_valid_input(String input, BigInteger expected) {

        // when
        BigInteger actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_valid_input() {
        return Stream.of(

                // Plain text
                Arguments.of("42", BigInteger.valueOf(42)),
                Arguments.of("   42   ", BigInteger.valueOf(42)),
                Arguments.of("-5", BigInteger.valueOf(-5)),
                Arguments.of("0", BigInteger.ZERO),
                Arguments.of("123456789012345678901234567890", new BigInteger("123456789012345678901234567890")),

                // JSON
                Arguments.of("{\"value\": 123}", BigInteger.valueOf(123)),
                Arguments.of("{\"value\": \"-7\"}", BigInteger.valueOf(-7)),
                Arguments.of("   {\"value\": 77}   ", BigInteger.valueOf(77)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}", "{\"value\": null}", "{\"value\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.math.BigInteger");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"value\":\"xyz\"}", "abc", "42abc", "1.5"})
    void should_fail_to_parse_invalid_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.math.BigInteger");
    }

    @Test
    void format_instructions() {
        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("integer number");
    }
}
