package dev.langchain4j.service.output;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
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
        EnumListOutputParser<Animal> parser = new EnumListOutputParser<>(Animal.class);

        // when
        List<Animal> animals = parser.parse(text);

        // then
        assertThat(animals).isEqualTo(expected);
    }

    static Stream<Arguments> should_parse_list_of_enums() {
        return Stream.of(

                // Plain text
                Arguments.of("CAT", List.of(CAT)),
                Arguments.of("CAT\nDOG", List.of(CAT, DOG)),
                Arguments.of("cat", List.of(CAT)),
                Arguments.of("Cat", List.of(CAT)),
                Arguments.of("", List.of()),
                Arguments.of(" ", List.of()),
                Arguments.of(" CAT ", List.of(CAT)),
                Arguments.of(" CAT \n DOG ", List.of(CAT, DOG)),

                // JSON
                Arguments.of("{\"values\":[\"CAT\"]}", List.of(CAT)),
                Arguments.of("{\"values\":[\"CAT\", \"DOG\"]}", List.of(CAT, DOG)),
                Arguments.of("{\"values\":[]}", List.of()),
                Arguments.of("  {\"values\":[\"CAT\"]}  ", List.of(CAT))
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"values\": null}"})
    void should_fail_to_parse_empty_input(String input) {

        assertThatThrownBy(() -> new EnumListOutputParser<>(Animal.class).parse(input))
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
        EnumListOutputParser<Animal> parser = new EnumListOutputParser<>(Animal.class);

        // when-then
        assertThatThrownBy(() -> parser.parse(text))
                .isExactlyInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Failed to parse")
                .hasMessageContaining("Animal");
    }

    @Test
    void should_create_schema_for_enum_with_custom_toString() {

        // given
        enum MyEnumWithToString {
            A, B, C;

            @Override
            public String toString() {
                return "[" + name() + "]";
            }
        }

        assertThat(MyEnumWithToString.A.toString()).isEqualTo("[A]");

        EnumListOutputParser<MyEnumWithToString> parser = new EnumListOutputParser<>(MyEnumWithToString.class);

        // when
        Optional<JsonSchema> jsonSchema = parser.jsonSchema();

        // then
        assertThat(jsonSchema).hasValue(JsonSchema.builder()
                .name("List_of_MyEnumWithToString")
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("values", JsonArraySchema.builder()
                                .items(JsonEnumSchema.builder()
                                        .enumValues("A", "B", "C")
                                        .build())
                                .build())
                        .required("values")
                        .build())
                .build());
    }
}
