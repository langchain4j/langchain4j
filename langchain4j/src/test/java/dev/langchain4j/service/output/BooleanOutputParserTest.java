package dev.langchain4j.service.output;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanOutputParserTest {

    @ParameterizedTest
    @MethodSource
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

                // Plain text: valid booleans (case-insensitive)
                Arguments.of("true", true),
                Arguments.of("false", false),
                Arguments.of("TRUE", true),
                Arguments.of("False", false),

                // Plain text: surrounded by whitespaces
                Arguments.of("    true    ", true),

                // JSON: has \"value\" key
                Arguments.of("{\"value\": true}", true),
                Arguments.of("{\"value\": false}", false),

                Arguments.of("{\"value\": \"TRUE\"}", true),
                Arguments.of("{\"value\": \"False\"}", false),

                // JSON: first property fallback if no \"value\"
                Arguments.of("{\"other\": true}", true),
                Arguments.of("{\"other\": false}", false),
                Arguments.of("{\"a\": true, \"b\": false}", true)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}", "{\"value\": null}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new BooleanOutputParser().parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Boolean");
    }

    @ParameterizedTest
    @ValueSource(strings = {"banana", "Answer: true", "Sorry, I cannot answer", "{\"value\": \"banana\"}"})
    void should_fail_to_parse_illegal_input(String input) {

        assertThatThrownBy(() -> new BooleanOutputParser().parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Boolean");
    }
}
