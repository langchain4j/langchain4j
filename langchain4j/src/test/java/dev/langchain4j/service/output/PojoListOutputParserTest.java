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

class PojoListOutputParserTest {

    record Person(String name) {
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_list_of_pojo(String json, List<Person> expected) {

        // when
        List<Person> people = new PojoListOutputParser<>(Person.class).parse(json);

        // then
        assertThat(people).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_list_of_pojo() {
        return Stream.of(

                Arguments.of(
                        "{\"items\":[{\"name\":\"Klaus\"}]}",
                        List.of(new Person("Klaus"))
                ),
                Arguments.of(
                        "{\"items\":[{\"name\":\"Klaus\"}, {\"name\":\"Franny\"}]}",
                        List.of(new Person("Klaus"), new Person("Franny"))
                ),

                // empty
                Arguments.of("", List.of()),
                Arguments.of(" ", List.of()),
                Arguments.of("{\"items\":[]}", List.of()),

                // wrong property name
                Arguments.of(
                        "{\"values\":[{\"name\":\"Klaus\"}]}",
                        List.of(new Person("Klaus"))
                ),
                Arguments.of(
                        "{\"people\":[{\"name\":\"Klaus\"}]}",
                        List.of(new Person("Klaus"))
                ),

                // surrounded by whitespaces
                Arguments.of(
                        " {\"items\":[{\"name\":\"Klaus\"}]} ",
                        List.of(new Person("Klaus"))
                )
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"items\": null}", "{\"items\": \"\"}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new PojoListOutputParser<>(Person.class).parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("Person");
    }
}
