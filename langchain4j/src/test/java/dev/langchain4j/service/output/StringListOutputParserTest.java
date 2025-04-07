package dev.langchain4j.service.output;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * Provides arguments for testing plain-text and JSON-based inputs.
     */
    static Stream<Arguments> should_parse_list_of_strings() {
        return Stream.of(
                // Plain text
                Arguments.of("CAT", List.of("CAT")),
                Arguments.of("CAT\nDOG", List.of("CAT", "DOG")),

                // Plain text: empty / null
                Arguments.of(null, List.of()),
                Arguments.of("", List.of()),
                Arguments.of(" ", List.of()),

                // Plain text: surrounded by whitespaces
                Arguments.of("  CAT  ", List.of("CAT")),
                Arguments.of(" CAT \n DOG ", List.of("CAT", "DOG")),

                // JSON with "items" property
                Arguments.of("{\"items\":[\"CAT\"]}", List.of("CAT")),
                Arguments.of("{\"items\":[\"CAT\",\"DOG\"]}", List.of("CAT", "DOG")),
                Arguments.of("{\"items\":[\"CAT\",\"DOG\",\"BIRD\"]}", List.of("CAT", "DOG", "BIRD")),

                // JSON with alternative property name
                Arguments.of("{\"values\":[\"CAT\"]}", List.of("CAT")),
                Arguments.of("{\"animals\":[\"CAT\",\"DOG\"]}", List.of("CAT", "DOG")),

                // JSON: empty
                Arguments.of("{}", List.of()),
                Arguments.of("{\"items\":[]}", List.of()),
                Arguments.of("{\"items\":null}", List.of()),

                // JSON: single string instead of array
                Arguments.of("{\"items\":\"CAT\"}", List.of("CAT")),

                // JSON: whitespaces
                Arguments.of("   {\"items\":[\"CAT\"]}   ", List.of("CAT"))
        );
    }

    /**
     * Example of testing with data that might not fail but checks other edge cases
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "BANANA",
            "{\"items\":[\"\"]}",
            "{\"items\":[\" \"]}"
    })
    void should_parse_list_of_strings_edge_cases(String text) {
        // given
        StringListOutputParser parser = new StringListOutputParser();

        // when
        List<String> result = parser.parse(text);

        // then
        // Here we just assert that parsing runs, but you can adapt as needed
        // For "BANANA", parser will return ["BANANA"] if it's plain text
        // For "{\"items\":[\"\"]}", it returns [""] (list with one blank string)
        // For "{\"items\":[\" \"]}", it returns [" "] (list with one space-only string)
        // Adjust your assertions to match your exact needs:
        if ("BANANA".equals(text)) {
            assertThat(result).isEqualTo(List.of("BANANA"));
        } else if ("{\"items\":[\"\"]}".equals(text)) {
            assertThat(result).isEqualTo(List.of(""));
        } else if ("{\"items\":[\" \"]}".equals(text)) {
            assertThat(result).isEqualTo(List.of(" "));
        }
    }
}
