package dev.langchain4j.service.output;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PojoSetOutputParserTest {

    record Person(String name) {
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_set_of_pojo(String json, Set<Person> expected) {

        // when
        Set<Person> people = new PojoSetOutputParser<>(Person.class).parse(json);

        // then
        assertThat(people).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_set_of_pojo() {
        return Stream.of(

                Arguments.of(
                        "{\"items\":[{\"name\":\"Klaus\"}]}",
                        Set.of(new Person("Klaus"))
                ),
                Arguments.of(
                        "{\"items\":[{\"name\":\"Klaus\"}, {\"name\":\"Franny\"}]}",
                        Set.of(new Person("Klaus"), new Person("Franny"))
                ),

                // empty
                Arguments.of("", Set.of()),
                Arguments.of(" ", Set.of()),
                Arguments.of("{\"items\":[]}", Set.of()),

                // wrong property name
                Arguments.of(
                        "{\"values\":[{\"name\":\"Klaus\"}]}",
                        Set.of(new Person("Klaus"))
                ),
                Arguments.of(
                        "{\"people\":[{\"name\":\"Klaus\"}]}",
                        Set.of(new Person("Klaus"))
                ),

                // surrounded by whitespaces
                Arguments.of(
                        " {\"items\":[{\"name\":\"Klaus\"}]} ",
                        Set.of(new Person("Klaus"))
                )
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"items\": null}", "{\"items\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new PojoSetOutputParser<>(Person.class).parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("Person");
    }
}
