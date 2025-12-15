package dev.langchain4j.service.output;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LongOutputParserTest {

    private final LongOutputParser parser = new LongOutputParser();

    @ParameterizedTest
    @MethodSource
    void should_parse_valid_input(String input, Long expected) {

        // when
        Long actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_valid_input() {
        return Stream.of(

                // Plain text
                Arguments.of("42", 42L),
                Arguments.of("   42   ", 42L),
                Arguments.of("-5", -5L),
                Arguments.of("0", 0L),
                Arguments.of("123.0", 123L),
                Arguments.of(String.valueOf(Long.MAX_VALUE), Long.MAX_VALUE),
                Arguments.of(String.valueOf(Long.MIN_VALUE), Long.MIN_VALUE),

                // JSON
                Arguments.of("{\"value\": 123}", 123L),
                Arguments.of("{\"value\": \"-7\"}", -7L),
                Arguments.of("   {\"value\": 77}   ", 77L),
                Arguments.of("{\"value\":\"1e3\"}", 1000L)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}", "{\"value\": null}", "{\"value\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Long");
    }

    @ParameterizedTest
    @MethodSource
    void should_fail_to_parse_invalid_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Long");
    }

    static Stream<String> should_fail_to_parse_invalid_input() {
        return Stream.of(
                "{\"value\":\"xyz\"}",
                "{\"value\": 3.14}",
                "{\"foo\":\"2.71\"}",

                "abc",
                "42abc",
                "4.5",
                "-3.1",

                Long.MAX_VALUE + "0",
                Long.MIN_VALUE + "0"
        );
    }
}
