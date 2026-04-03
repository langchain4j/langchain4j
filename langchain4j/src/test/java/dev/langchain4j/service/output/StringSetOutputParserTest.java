package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class StringSetOutputParserTest {

    StringSetOutputParser sut = new StringSetOutputParser();

    @Test()
    void ensureThatOrderIsPreserved() {
        // Given
        String toParse = "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten";

        // When
        Set<String> parsedSet = sut.parse(toParse);

        // Then
        Iterator<String> setIterator = parsedSet.iterator();
        assertThat("one").isEqualTo(setIterator.next());
        assertThat("two").isEqualTo(setIterator.next());
        assertThat("three").isEqualTo(setIterator.next());
        assertThat("four").isEqualTo(setIterator.next());
        assertThat("five").isEqualTo(setIterator.next());
        assertThat("six").isEqualTo(setIterator.next());
        assertThat("seven").isEqualTo(setIterator.next());
        assertThat("eight").isEqualTo(setIterator.next());
        assertThat("nine").isEqualTo(setIterator.next());
        assertThat("ten").isEqualTo(setIterator.next());
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_set_of_strings(String text, Set<String> expected) {
        Set<String> actual = sut.parse(text);
        assertThat(actual).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_set_of_strings() {
        return Stream.of(

                // Plain text
                Arguments.of("CAT", Set.of("CAT")),
                Arguments.of("CAT\nDOG\nBIRD\nCAT", Set.of("CAT", "DOG", "BIRD")),
                Arguments.of("", Set.of()),
                Arguments.of("    ", Set.of()),
                Arguments.of("  CAT  ", Set.of("CAT")),
                Arguments.of(" CAT \n DOG \n  DOG ", Set.of("CAT", "DOG")),

                // JSON
                Arguments.of("{\"values\":[\"CAT\"]}", Set.of("CAT")),
                Arguments.of("{\"values\":[\"CAT\",\"DOG\"]}", Set.of("CAT", "DOG")),
                Arguments.of("{\"values\":[\"CAT\",\"DOG\",\"CAT\"]}", Set.of("CAT", "DOG")),
                Arguments.of("{\"values\":[]}", Set.of()),
                Arguments.of("  {\"values\":[\"CAT\",\"DOG\"]}  ", Set.of("CAT", "DOG")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"values\": null}", "{\"banana\": []}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new StringSetOutputParser().parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("java.util.Set<java.lang.String>");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "{\"values\": \"\"}",
                "{\"values\": false}",
                "{\"values\":\"banana\"}",
                "{\"values\":{\"name\":\"Klaus\"}}",
                "{\"banana\":[{\"name\":\"Klaus\"}]}",
            })
    void should_fail_to_parse_invalid_input(String input) {

        assertThatThrownBy(() -> new StringSetOutputParser().parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("java.util.Set<java.lang.String>");
    }

    @Test
    void should_handle_json_with_escaped_quotes() {
        // Given
        String toParse = "{\"values\":[\"He said \\\"hello\\\"\",\"Quote: \\\"test\\\"\"]}";

        // When
        Set<String> parsedSet = sut.parse(toParse);

        // Then
        assertThat(parsedSet).containsExactlyInAnyOrder("He said \"hello\"", "Quote: \"test\"");
    }

    @Test
    void should_handle_single_line_with_multiple_separators() {
        // Given
        String toParse = "one\n\ntwo\n\n\nthree\n";

        // When
        Set<String> parsedSet = sut.parse(toParse);

        // Then
        assertThat(parsedSet).containsExactlyInAnyOrder("one", "two", "three");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"values\":[\"test\", \"duplicate\", \"test\"]}",
                "{\"values\":[\"A\", \"B\", \"A\", \"C\", \"B\"]}",
                "duplicate\nvalue\nduplicate\nvalue\nunique"
            })
    void should_handle_duplicates_correctly(String input) {
        // When
        Set<String> parsedSet = sut.parse(input);

        // Then
        if (input.startsWith("{")) {
            if (input.contains("test")) {
                assertThat(parsedSet).containsExactlyInAnyOrder("test", "duplicate");
            } else {
                assertThat(parsedSet).containsExactlyInAnyOrder("A", "B", "C");
            }
        } else {
            assertThat(parsedSet).containsExactlyInAnyOrder("duplicate", "value", "unique");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"values\":[]}", "{\n  \"values\": [\n  ]\n}", "{ \"values\" : [ ] }"})
    void should_handle_empty_json_arrays(String input) {
        // When
        Set<String> parsedSet = sut.parse(input);

        // Then
        assertThat(parsedSet).isEmpty();
    }

    @Test
    void should_preserve_internal_spaces_in_plain_text() {
        // Given
        String toParse = "hello world\ntest  value\n  leading space\ntrailing space  ";

        // When
        Set<String> parsedSet = sut.parse(toParse);

        // Then
        assertThat(parsedSet)
                .containsExactlyInAnyOrder("hello world", "test  value", "leading space", "trailing space");
    }
}
