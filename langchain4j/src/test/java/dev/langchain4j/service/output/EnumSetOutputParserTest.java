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
        EnumSetOutputParser<Animal> parser = new EnumSetOutputParser<>(Animal.class);

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
                Arguments.of("cat", Set.of(CAT)),
                Arguments.of("Cat", Set.of(CAT)),
                Arguments.of("", Set.of()),
                Arguments.of(" ", Set.of()),
                Arguments.of(" CAT ", Set.of(CAT)),
                Arguments.of(" CAT \n DOG ", Set.of(CAT, DOG)),

                // JSON
                Arguments.of("{\"values\":[\"CAT\"]}", Set.of(CAT)),
                Arguments.of("{\"values\":[\"CAT\", \"DOG\"]}", Set.of(CAT, DOG)),
                Arguments.of("{\"values\":[]}", Set.of()),
                Arguments.of("  {\"values\":[\"CAT\"]}  ", Set.of(CAT))
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"values\": null}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new EnumSetOutputParser<>(Animal.class).parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("Animal");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "BANANA",
            "{\"values\":[\"BANANA\"]}",
            "{\"values\":\"CAT\"}",
            "{\"banana\":[\"CAT\"]}"
    })
    void should_fail_to_parse_invalid_input(String text) {

        // given
        EnumSetOutputParser<Animal> parser = new EnumSetOutputParser<>(Animal.class);

        // when-then
        assertThatThrownBy(() -> parser.parse(text))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("Animal");
    }

    @Test
    void should_preserve_order() {

        // given
        EnumSetOutputParser<Animal> parser = new EnumSetOutputParser<>(Animal.class);

        // when
        Set<Animal> parsed = parser.parse("CAT\nDOG\nBIRD");

        // then
        Iterator<Animal> enumIterator = parsed.iterator();
        assertThat(enumIterator.next()).isEqualTo(CAT);
        assertThat(enumIterator.next()).isEqualTo(DOG);
        assertThat(enumIterator.next()).isEqualTo(BIRD);
    }

    @Test
    void should_preserve_order_JSON() {

        // given
        EnumSetOutputParser<Animal> parser = new EnumSetOutputParser<>(Animal.class);

        // when
        Set<Animal> parsed = parser.parse("{\"values\":[\"CAT\", \"DOG\", \"BIRD\"]}");

        // then
        Iterator<Animal> enumIterator = parsed.iterator();
        assertThat(enumIterator.next()).isEqualTo(CAT);
        assertThat(enumIterator.next()).isEqualTo(DOG);
        assertThat(enumIterator.next()).isEqualTo(BIRD);
    }
}
