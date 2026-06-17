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

class ByteOutputParserTest {

    private final ByteOutputParser parser = new ByteOutputParser();

    @ParameterizedTest
    @MethodSource
    void should_parse_valid_input(String input, Byte expected) {

        // when
        Byte actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_valid_input() {
        return Stream.of(

                // Plain text
                Arguments.of("42", (byte) 42),
                Arguments.of("   42   ", (byte) 42),
                Arguments.of("-5", (byte) -5),
                Arguments.of("0", (byte) 0),
                Arguments.of("12.0", (byte) 12),
                Arguments.of(String.valueOf(Byte.MAX_VALUE), Byte.MAX_VALUE),
                Arguments.of(String.valueOf(Byte.MIN_VALUE), Byte.MIN_VALUE),

                // JSON
                Arguments.of("{\"value\": 42}", (byte) 42),
                Arguments.of("{\"value\": \"-5\"}", (byte) -5),
                Arguments.of("   {\"value\": 77}   ", (byte) 77));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}", "{\"value\": null}", "{\"value\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Byte");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"value\":\"xyz\"}",
                "{\"value\": 1.5}",
                "abc",
                "42abc",
                "1.5",
                "128", // Byte.MAX_VALUE + 1
                "-129" // Byte.MIN_VALUE - 1
            })
    void should_fail_to_parse_invalid_input(String input) {

        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Byte");
    }

    @Test
    void format_instructions() {
        // when
        String instructions = parser.formatInstructions();

        // then
        assertThat(instructions).isEqualTo("integer number in range [-128, 127]");
    }
}
