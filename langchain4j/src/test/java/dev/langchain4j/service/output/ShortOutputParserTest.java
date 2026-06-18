package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ShortOutputParserTest {

    private final ShortOutputParser parser = new ShortOutputParser();

    @ParameterizedTest
    @MethodSource
    void should_parse_valid_input(String input, Short expected) {

        // when
        Short actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_valid_input() {
        return Stream.of(

                // Plain text
                Arguments.of("42", (short) 42),
                Arguments.of("   42   ", (short) 42),
                Arguments.of("-5", (short) -5),
                Arguments.of("0", (short) 0),
                Arguments.of("123.0", (short) 123),
                Arguments.of(String.valueOf(Short.MAX_VALUE), Short.MAX_VALUE),
                Arguments.of(String.valueOf(Short.MIN_VALUE), Short.MIN_VALUE),

                // JSON
                Arguments.of("{\"value\": 123}", (short) 123),
                Arguments.of("{\"value\": \"-7\"}", (short) -7),
                Arguments.of("   {\"value\": 77}   ", (short) 77));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}", "{\"value\": null}", "{\"value\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Short");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"value\":\"xyz\"}",
                "{\"value\": 3.14}",
                "abc",
                "42abc",
                "4.5",
                "32768", // Short.MAX_VALUE + 1
                "-32769" // Short.MIN_VALUE - 1
            })
    void should_fail_to_parse_invalid_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Short");
    }

    @Test
    void format_instructions() {
        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("integer number in range [-32768, 32767]");
    }
}
