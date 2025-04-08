package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                // Plain text: single line
                Arguments.of("CAT", Set.of("CAT")),

                // Plain text: multiple lines (duplicates should collapse)
                Arguments.of("CAT\nDOG\nBIRD\nCAT", Set.of("CAT", "DOG", "BIRD")),

                // Plain text: empty
                Arguments.of("", Set.of()),
                Arguments.of("    ", Set.of()),

                // Plain text: surrounded by whitespaces
                Arguments.of("  CAT  ", Set.of("CAT")),
                Arguments.of(" CAT \n DOG \n  DOG ", Set.of("CAT", "DOG")),

                // JSON with "items" property
                Arguments.of("{\"items\":[\"CAT\"]}", Set.of("CAT")),
                Arguments.of("{\"items\":[\"CAT\",\"DOG\"]}", Set.of("CAT", "DOG")),
                Arguments.of("{\"items\":[\"CAT\",\"DOG\",\"CAT\"]}", Set.of("CAT", "DOG")),

                // JSON without "items" property (fallback to first property)
                Arguments.of("{\"values\":[\"CAT\"]}", Set.of("CAT")),
                Arguments.of("{\"animals\":[\"CAT\",\"DOG\"]}", Set.of("CAT", "DOG")),

                // JSON: empty
                Arguments.of("{\"items\":[]}", Set.of()),

                // JSON: single string
                Arguments.of("{\"items\":\"CAT\"}", Set.of("CAT")),

                // JSON: whitespaces
                Arguments.of("   {\"items\":[\"CAT\",\"DOG\"]}   ", Set.of("CAT", "DOG"))
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"items\": null}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new StringSetOutputParser().parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("java.util.Set<java.lang.String>");
    }
}
