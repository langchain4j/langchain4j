package dev.langchain4j.service.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BooleanOutputParserTest {

    @ParameterizedTest
    @MethodSource
    @DisplayName("Should parse string input to boolean")
    void should_parse_string_to_boolean(String input, Boolean expected) {
        // given
        BooleanOutputParser parser = new BooleanOutputParser();

        // when
        Boolean actual = parser.parse(input);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_string_to_boolean() {
        return Stream.of(
                // Plain text: null, empty, whitespace => defaults to false
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("  ", false),

                // Plain text: valid booleans (case-insensitive)
                Arguments.of("true", true),
                Arguments.of("false", false),
                Arguments.of("TrUe", true),
                Arguments.of("FaLsE", false),

                // Plain text: random string => false
                Arguments.of("banana", false),

                // Plain text: trimmed input
                Arguments.of("    true    ", true),

                // JSON: empty map => false
                Arguments.of("{}", false),

                // JSON: has \"value\" key
                Arguments.of("{\"value\": true}", true),
                Arguments.of("{\"value\": \"true\"}", true),
                Arguments.of("{\"value\":\"TrUe\"}", true),
                Arguments.of("{\"value\":\"false\"}", false),
                Arguments.of("{\"value\":\"FaLsE\"}", false),
                Arguments.of("{\"value\": null}", false),

                // JSON: first property fallback if no \"value\"
                Arguments.of("{\"other\": \"true\"}", true),
                Arguments.of("{\"other\": \"false\"}", false),
                Arguments.of("{\"other\": \"BANANA\"}", false),
                Arguments.of("{\"a\": true, \"b\": false}", true),  // first prop is "a" => true
                Arguments.of("{\"a\": \"FaLsE\", \"b\":true}", false)
        );
    }
}

