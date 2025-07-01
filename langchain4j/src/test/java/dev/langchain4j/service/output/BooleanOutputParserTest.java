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

                // Plain text
                Arguments.of("true", true),
                Arguments.of("false", false),
                Arguments.of("TRUE", true),
                Arguments.of("False", false),
                Arguments.of("    true    ", true),

                // JSON
                Arguments.of("{\"value\": true}", true),
                Arguments.of("{\"value\": \"TRUE\"}", true),
                Arguments.of("{\"value\": false}", false),
                Arguments.of("{\"value\": \"False\"}", false),
                Arguments.of("  {\"value\": \"False\"}  ", false)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "{}", "{\"value\": null}", "{\"value\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new BooleanOutputParser().parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Boolean");
    }

    @ParameterizedTest
    @ValueSource(strings = {"banana", "Answer: true", "Sorry, I cannot answer", "{\"value\": \"banana\"}", "{\"banana\": true}"})
    void should_fail_to_parse_invalid_input(String input) {

        assertThatThrownBy(() -> new BooleanOutputParser().parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("into java.lang.Boolean");
    }
}
