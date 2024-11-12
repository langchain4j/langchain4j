package dev.langchain4j.service.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import static dev.langchain4j.service.output.EnumSetOutputParserTest.Animal.BIRD;
import static dev.langchain4j.service.output.EnumSetOutputParserTest.Animal.CAT;
import static dev.langchain4j.service.output.EnumSetOutputParserTest.Animal.DOG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumSetOutputParserTest {

    enum Animal {
        CAT, DOG, BIRD
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_set_of_enums(String text, Set<Animal> expected) {

        // given
        EnumSetOutputParser parser = new EnumSetOutputParser(Animal.class);

        // when
        Set<?> animals = parser.parse(text);

        // then
        assertThat(animals).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_set_of_enums() {
        return Stream.of(

            // Plain text
            Arguments.of("CAT", Set.of(CAT)),
            Arguments.of("CAT\nDOG", Set.of(CAT, DOG)),

            // Plain text: wrong case
            Arguments.of("cat", Set.of(CAT)),
            Arguments.of("Cat", Set.of(CAT)),

            // Plain text: empty
            Arguments.of("", Set.of()),
            Arguments.of(" ", Set.of()),
            Arguments.of(null, Set.of()),

            // JSON
            Arguments.of("{\"items\":[CAT]}", Set.of(CAT)),
            Arguments.of("{\"items\":['CAT']}", Set.of(CAT)),
            Arguments.of("{\"items\":[\"CAT\"]}", Set.of(CAT)),
            Arguments.of("{\"items\":[CAT, DOG]}", Set.of(CAT, DOG)),

            // JSON: wrong case
            Arguments.of("{\"items\":[cat]}", Set.of(CAT)),
            Arguments.of("{\"items\":[Cat]}", Set.of(CAT)),

            // JSON: empty
            Arguments.of("{}", Set.of()),
            Arguments.of("{\"items\":[]}", Set.of()),
            Arguments.of("{\"items\":null}", Set.of()),

            // JSON: wrong type
            Arguments.of("{\"items\":\"CAT\"}", Set.of(CAT)),

            // JSON: wrong property name
            Arguments.of("{\"values\":[CAT]}", Set.of(CAT)),
            Arguments.of("{\"animals\":[CAT]}", Set.of(CAT)),

            // JSON surrounded by whitespaces
            Arguments.of(" {\"items\":[CAT]} ", Set.of(CAT))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "BANANA",
        "{\"items\":[BANANA]}"
    })
    void should_fail_to_parse_set_of_enums(String text) {

        // given
        EnumSetOutputParser parser = new EnumSetOutputParser(Animal.class);

        // when-then
        assertThatThrownBy(() -> parser.parse(text))
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessageContaining("Unknown enum value: BANANA");
    }

    @Test()
    void should_preserve_order() {

        // given
        EnumSetOutputParser parser = new EnumSetOutputParser(Animal.class);

        // when
        Set<Enum> parsed = parser.parse("CAT\nDOG\nBIRD");

        // then
        Iterator<Enum> enumIterator = parsed.iterator();
        assertThat(enumIterator.next()).isEqualTo(CAT);
        assertThat(enumIterator.next()).isEqualTo(DOG);
        assertThat(enumIterator.next()).isEqualTo(BIRD);
    }

    @Test()
    void should_preserve_order_JSON() {

        // given
        EnumSetOutputParser parser = new EnumSetOutputParser(Animal.class);

        // when
        Set<Enum> parsed = parser.parse("{\"items\":[CAT, DOG, BIRD]}");

        // then
        Iterator<Enum> enumIterator = parsed.iterator();
        assertThat(enumIterator.next()).isEqualTo(CAT);
        assertThat(enumIterator.next()).isEqualTo(DOG);
        assertThat(enumIterator.next()).isEqualTo(BIRD);
    }
}
