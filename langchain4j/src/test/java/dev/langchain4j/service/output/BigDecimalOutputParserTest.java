package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BigDecimalOutputParserTest {

    private final BigDecimalOutputParser parser = new BigDecimalOutputParser();

    @ParameterizedTest
    @MethodSource
    void should_parse_valid_input(String input, BigDecimal expected) {

        // when
        BigDecimal actual = parser.parse(input);

        // then
        assertThat(actual).isEqualByComparingTo(expected);
    }

    static Stream<Arguments> should_parse_valid_input() {
        return Stream.of(

                // Plain text
                Arguments.of("42", new BigDecimal("42")),
                Arguments.of("   3.14   ", new BigDecimal("3.14")),
                Arguments.of("-2.5", new BigDecimal("-2.5")),
                Arguments.of("0", new BigDecimal("0")),

                // JSON
                Arguments.of("{\"value\": 3.14}", new BigDecimal("3.14")),
                Arguments.of("{\"value\": \"-2.5\"}", new BigDecimal("-2.5")),
                Arguments.of("   {\"value\": 77}   ", new BigDecimal("77")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}", "{\"value\": null}", "{\"value\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.math.BigDecimal");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"value\":\"xyz\"}", "abc", "42abc", "1.2.3"})
    void should_fail_to_parse_invalid_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.math.BigDecimal");
    }

    @Test
    void format_instructions() {
        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("floating point number");
    }
}
