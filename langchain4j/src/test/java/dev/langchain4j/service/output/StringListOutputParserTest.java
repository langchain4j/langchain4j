package dev.langchain4j.service.output;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringListOutputParserTest {

    @ParameterizedTest
    @MethodSource
    void should_parse_list_of_strings(String text, List<String> expected) {

        // given
        StringListOutputParser parser = new StringListOutputParser();

        // when
        List<String> result = parser.parse(text);

        // then
        assertThat(result).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_list_of_strings() {
        return Stream.of(
                // Plain text
                Arguments.of("CAT", List.of("CAT")),
                Arguments.of("CAT\nDOG", List.of("CAT", "DOG")),

                // Plain text: empty
                Arguments.of("", List.of()),
                Arguments.of(" ", List.of()),

                // Plain text: surrounded by whitespaces
                Arguments.of("  CAT  ", List.of("CAT")),
                Arguments.of(" CAT \n DOG ", List.of("CAT", "DOG")),

                // JSON with "items" property
                Arguments.of("{\"items\":[\"CAT\"]}", List.of("CAT")),
                Arguments.of("{\"items\":[\"CAT\",\"DOG\"]}", List.of("CAT", "DOG")),

                // JSON with alternative property name
                Arguments.of("{\"values\":[\"CAT\"]}", List.of("CAT")),
                Arguments.of("{\"animals\":[\"CAT\",\"DOG\"]}", List.of("CAT", "DOG")),

                // JSON: empty
                Arguments.of("{\"items\":[]}", List.of()),

                // JSON: single string instead of array
                Arguments.of("{\"items\":\"CAT\"}", List.of("CAT")),

                // JSON: whitespaces
                Arguments.of("   {\"items\":[\"CAT\"]}   ", List.of("CAT"))
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"items\": null}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new StringListOutputParser().parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("java.util.List<java.lang.String>");
    }
}
