package dev.langchain4j.service.output;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.service.output.EnumListOutputParserTest.Animal.CAT;
import static dev.langchain4j.service.output.EnumListOutputParserTest.Animal.DOG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumListOutputParserTest {

    enum Animal {
        CAT, DOG, BIRD
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_list_of_enums(String text, List<Animal> expected) {

        // given
        EnumListOutputParser parser = new EnumListOutputParser(Animal.class);

        // when
        List<?> animals = parser.parse(text);

        // then
        assertThat(animals).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_list_of_enums() {
        return Stream.of(

            // Plain text
            Arguments.of("CAT", List.of(CAT)),
            Arguments.of("CAT\nDOG", List.of(CAT, DOG)),

            // Plain text: wrong case
            Arguments.of("cat", List.of(CAT)),
            Arguments.of("Cat", List.of(CAT)),

            // Plain text: empty
            Arguments.of("", List.of()),
            Arguments.of(" ", List.of()),
            Arguments.of(null, List.of()),

            // JSON
            Arguments.of("{\"items\":[CAT]}", List.of(CAT)),
            Arguments.of("{\"items\":['CAT']}", List.of(CAT)),
            Arguments.of("{\"items\":[\"CAT\"]}", List.of(CAT)),
            Arguments.of("{\"items\":[CAT, DOG]}", List.of(CAT, DOG)),

            // JSON: wrong case
            Arguments.of("{\"items\":[cat]}", List.of(CAT)),
            Arguments.of("{\"items\":[Cat]}", List.of(CAT)),

            // JSON: empty
            Arguments.of("{}", List.of()),
            Arguments.of("{\"items\":[]}", List.of()),
            Arguments.of("{\"items\":null}", List.of()),

            // JSON: wrong type
            Arguments.of("{\"items\":\"CAT\"}", List.of(CAT)),

            // JSON: wrong property name
            Arguments.of("{\"values\":[CAT]}", List.of(CAT)),
            Arguments.of("{\"animals\":[CAT]}", List.of(CAT))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "BANANA",
        "{\"items\":[BANANA]}"
    })
    void should_fail_to_parse_list_of_enums(String text) {

        // given
        EnumListOutputParser parser = new EnumListOutputParser(Animal.class);

        // when-then
        assertThatThrownBy(() -> parser.parse(text))
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessageContaining("Unknown enum value: BANANA");
    }
}
