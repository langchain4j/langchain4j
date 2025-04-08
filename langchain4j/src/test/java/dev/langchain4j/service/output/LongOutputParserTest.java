package dev.langchain4j.service.output;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LongOutputParserTest {

    private final LongOutputParser parser = new LongOutputParser();

    @ParameterizedTest
    @MethodSource("validInputProvider")
    void should_parse_valid_input(String input, Long expected) {

        // when
        Long actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> validInputProvider() {
        return Stream.of(
                // Plain longs
                Arguments.of("42", 42L),
                Arguments.of("   42   ", 42L),
                Arguments.of("-5", -5L),
                Arguments.of("0", 0L),
                Arguments.of("123.0", 123L),
                Arguments.of(String.valueOf(Long.MAX_VALUE), Long.MAX_VALUE),
                Arguments.of(String.valueOf(Long.MIN_VALUE), Long.MIN_VALUE),

                // JSON with "value" key
                Arguments.of("{\"value\": 123}", 123L),
                Arguments.of("{\"value\": \"-7\"}", -7L),

                // JSON fallback to first property
                Arguments.of("{\"foo\": 100}", 100L),
                Arguments.of("{\"bar\":\"250\"}", 250L),

                // Surrounded by whitespace
                Arguments.of("   {\"value\": 77}   ", 77L),
                Arguments.of("{\"value\":\"1e3\"}", 1000L)
        );
    }

    @ParameterizedTest
    @MethodSource("noDataInputProvider")
    void should_fail_on_no_data_input(String input) {
        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Long");
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
    void should_fail_on_invalid_non_long_json_input(String input) {
        assertThatThrownBy(() -> parser.parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Long");
    }

    static Stream<String> invalidInputProvider() {
        return Stream.of(
                // JSON with non-long or decimal
                "{\"value\":\"xyz\"}",
                "{\"value\": 3.14}",
                "{\"foo\":\"2.71\"}",

                // Non-numeric text
                "abc",
                "42abc",
                "4.5",
                "-3.1",

                // out of bounds
                Long.MAX_VALUE + "0",
                Long.MIN_VALUE + "0"
        );
    }
}
