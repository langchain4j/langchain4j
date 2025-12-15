package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PojoListOutputParserTest {

    record Person(String name) {}

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
                Arguments.of("{\"values\":[{\"name\":\"Klaus\"}]}", List.of(new Person("Klaus"))),
                Arguments.of(
                        "{\"values\":[{\"name\":\"Klaus\"}, {\"name\":\"Franny\"}]}",
                        List.of(new Person("Klaus"), new Person("Franny"))),
                Arguments.of("", List.of()),
                Arguments.of(" ", List.of()),
                Arguments.of("{\"values\":[]}", List.of()),
                Arguments.of(" {\"values\":[{\"name\":\"Klaus\"}]} ", List.of(new Person("Klaus"))));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"values\": null}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new PojoListOutputParser<>(Person.class).parse(input))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("Person");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "banana",
                "{\"values\": \"\"}",
                "{\"values\":\"banana\"}",
                "{\"values\":[\"banana\"]}",
                "{\"values\":{\"name\":\"Klaus\"}}",
                "{\"banana\":[{\"name\":\"Klaus\"}]}",
            })
    void should_fail_to_parse_invalid_input(String text) {

        assertThatThrownBy(() -> new PojoListOutputParser<>(Person.class).parse(text))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("Person");
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_person_with_null_name(String json, List<Person> expected) {
        // when
        List<Person> people = new PojoListOutputParser<>(Person.class).parse(json);

        // then
        assertThat(people).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_person_with_null_name() {
        return Stream.of(
                Arguments.of("{\"values\":[{\"name\":null}]}", List.of(new Person(null))),
                Arguments.of(
                        "{\"values\":[{\"name\":\"Alice\"},{\"name\":null},{\"name\":\"Bob\"}]}",
                        List.of(new Person("Alice"), new Person(null), new Person("Bob"))));
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_person_with_missing_name(String json, List<Person> expected) {
        // when
        List<Person> people = new PojoListOutputParser<>(Person.class).parse(json);

        // then
        assertThat(people).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_person_with_missing_name() {
        return Stream.of(
                Arguments.of("{\"values\":[{}]}", List.of(new Person(null))),
                Arguments.of(
                        "{\"values\":[{},{\"name\":\"Alice\"},{}]}",
                        List.of(new Person(null), new Person("Alice"), new Person(null))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"values\":{\"name\":\"Alice\"}}",
                "[{\"name\":\"Alice\"}]",
                "{\"values\":[\"Alice\"]}",
            })
    void should_fail_to_parse_malformed_json(String malformedJson) {
        assertThatThrownBy(() -> new PojoListOutputParser<>(Person.class).parse(malformedJson))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"values\":[{\"name\":[\"array\"]}]}",
                "{\"values\":[{\"name\":{\"object\":\"value\"}}]}",
            })
    void should_fail_to_parse_wrong_type_for_name(String json) {
        assertThatThrownBy(() -> new PojoListOutputParser<>(Person.class).parse(json))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse");
    }

    @ParameterizedTest
    @MethodSource
    void should_handle_whitespace_in_json(String json, List<Person> expected) {
        // when
        List<Person> people = new PojoListOutputParser<>(Person.class).parse(json);

        // then
        assertThat(people).isEqualTo(expected);
    }

    static Stream<Arguments> should_handle_whitespace_in_json() {
        return Stream.of(
                Arguments.of("  {  \"values\"  :  [  ]  }  ", List.of()),
                Arguments.of("\n{\n\"values\"\n:\n[\n]\n}\n", List.of()),
                Arguments.of("\t{\t\"values\"\t:\t[\t]\t}\t", List.of()),
                Arguments.of(
                        "   {   \"values\"   :   [   {   \"name\"   :   \"Alice\"   }   ]   }   ",
                        List.of(new Person("Alice"))));
    }

    @ParameterizedTest
    @MethodSource
    void should_parse_escaped_characters(String json, List<Person> expected) {
        // when
        List<Person> people = new PojoListOutputParser<>(Person.class).parse(json);

        // then
        assertThat(people).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_escaped_characters() {
        return Stream.of(
                Arguments.of("{\"values\":[{\"name\":\"Alice\\\"Bob\"}]}", List.of(new Person("Alice\"Bob"))),
                Arguments.of("{\"values\":[{\"name\":\"Line\\nBreak\"}]}", List.of(new Person("Line\nBreak"))),
                Arguments.of("{\"values\":[{\"name\":\"Tab\\tCharacter\"}]}", List.of(new Person("Tab\tCharacter"))),
                Arguments.of("{\"values\":[{\"name\":\"Back\\\\slash\"}]}", List.of(new Person("Back\\slash"))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"values\":[{\"name\":\"Alice\",\"name\":\"Bob\"}]}",
                "{\"values\":[{\"name\":\"Alice\"}],\"values\":[{\"name\":\"Bob\"}]}",
            })
    void should_handle_or_fail_duplicate_keys(String json) {
        try {
            List<Person> people = new PojoListOutputParser<>(Person.class).parse(json);
            assertThat(people).isNotNull();
        } catch (OutputParsingException e) {
            assertThat(e.getMessage()).contains("Failed to parse");
        }
    }
}
