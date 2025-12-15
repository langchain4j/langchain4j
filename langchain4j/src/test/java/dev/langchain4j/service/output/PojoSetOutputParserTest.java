package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PojoSetOutputParserTest {

    record Person(String name) {}

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
                Arguments.of("{\"values\":[{\"name\":\"Klaus\"}]}", Set.of(new Person("Klaus"))),
                Arguments.of(
                        "{\"values\":[{\"name\":\"Klaus\"}, {\"name\":\"Franny\"}]}",
                        Set.of(new Person("Klaus"), new Person("Franny"))),
                Arguments.of("", Set.of()),
                Arguments.of(" ", Set.of()),
                Arguments.of("{\"values\":[]}", Set.of()),
                Arguments.of(" {\"values\":[{\"name\":\"Klaus\"}]} ", Set.of(new Person("Klaus"))));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"values\": null}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new PojoSetOutputParser<>(Person.class).parse(input))
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

        // when-then
        assertThatThrownBy(() -> new PojoSetOutputParser<>(Person.class).parse(text))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("Person");
    }

    @Test
    void should_handle_duplicate_values_in_set() {
        // Given JSON with duplicate entries
        String json = "{\"values\":[{\"name\":\"Value1\"}, {\"name\":\"Value1\"}, {\"name\":\"Value2\"}]}";

        // When parsing
        Set<Person> people = new PojoSetOutputParser<>(Person.class).parse(json);

        // Then duplicates are removed (Set behavior)
        assertThat(people).hasSize(2);
        assertThat(people).containsExactlyInAnyOrder(new Person("Value1"), new Person("Value2"));
    }

    @Test
    void should_handle_nested_objects() {
        record Config(String key, String value) {}
        record Item(String name, Config config) {}

        String json = "{\"values\":[{\"name\":\"Item1\",\"config\":{\"key\":\"Key1\",\"value\":\"Value1\"}}]}";

        Set<Item> items = new PojoSetOutputParser<>(Item.class).parse(json);

        assertThat(items).hasSize(1);
        assertThat(items.iterator().next().config().value()).isEqualTo("Value1");
    }

    @Test
    void should_handle_null_field_values() {
        String json = "{\"values\":[{\"name\":null}]}";

        Set<Person> people = new PojoSetOutputParser<>(Person.class).parse(json);

        assertThat(people).hasSize(1);
        assertThat(people.iterator().next().name()).isNull();
    }

    @Test
    void should_handle_missing_required_fields() {
        // JSON missing the 'name' field
        String json = "{\"values\":[{}]}";

        Set<Person> people = new PojoSetOutputParser<>(Person.class).parse(json);

        assertThat(people).hasSize(1);
        assertThat(people.iterator().next().name()).isNull();
    }

    @Test
    void should_handle_escaped_characters_in_json() {
        String json = "{\"values\":[{\"name\":\"Value:\\\"test\\\"\"}]}";

        Set<Person> people = new PojoSetOutputParser<>(Person.class).parse(json);

        assertThat(people).hasSize(1);
        assertThat(people.iterator().next().name()).isEqualTo("Value:\"test\"");
    }

    @Test
    void should_preserve_order_independence_of_set() {
        String json1 = "{\"values\":[{\"name\":\"Value1\"},{\"name\":\"Value2\"}]}";
        String json2 = "{\"values\":[{\"name\":\"Value2\"},{\"name\":\"Value1\"}]}";

        Set<Person> set1 = new PojoSetOutputParser<>(Person.class).parse(json1);
        Set<Person> set2 = new PojoSetOutputParser<>(Person.class).parse(json2);

        assertThat(set1).isEqualTo(set2);
    }
}
