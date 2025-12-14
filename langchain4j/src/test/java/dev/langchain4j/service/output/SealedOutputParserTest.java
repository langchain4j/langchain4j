package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.json.Polymorphic;
import dev.langchain4j.json.PolymorphicValue;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.service.IllegalConfigurationException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SealedOutputParserTest {

    @Polymorphic(discriminator = "type")
    sealed interface ChatbotResponse permits TextResponse, ImageResponse {}

    @PolymorphicValue("text")
    record TextResponse(String type, String text) implements ChatbotResponse {}

    @PolymorphicValue("image")
    record ImageResponse(String type, String url) implements ChatbotResponse {}

    private final SealedOutputParser<ChatbotResponse> parser = new SealedOutputParser<>(ChatbotResponse.class);

    @Polymorphic(discriminator = "type")
    private sealed interface BrokenMissing permits MissingValue {}

    record MissingValue(String type) implements BrokenMissing {}

    @Polymorphic(discriminator = "type")
    private sealed interface BrokenDuplicate permits First, Second {}

    @PolymorphicValue("dup")
    record First(String type) implements BrokenDuplicate {}

    @PolymorphicValue("dup")
    record Second(String type) implements BrokenDuplicate {}

    @Polymorphic(discriminator = "kind")
    private sealed interface Custom permits Cat, Dog {}

    @PolymorphicValue("cat")
    record Cat(String kind, String name) implements Custom {}

    @PolymorphicValue("dog")
    record Dog(String kind, String name) implements Custom {}

    @Test
    void should_parse_discriminated_subtype() {
        String json = """
                { "type": "text", "text": "hi" }
                """;

        ChatbotResponse response = parser.parse(json);

        assertThat(response).isInstanceOf(TextResponse.class);
        assertThat(((TextResponse) response).text()).isEqualTo("hi");
    }

    @Test
    void should_fail_when_discriminator_missing() {
        String json = """
                { "text": "hi" }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Missing discriminator")
                .hasMessageContaining("type");
    }

    @Test
    void should_fail_on_unknown_discriminator() {
        String json = """
                { "type": "unknown", "text": "hi" }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Unknown discriminator value");
    }

    @Test
    void should_fail_when_permitted_subclass_missing_annotation() {
        assertThatThrownBy(() -> new SealedOutputParser<>(BrokenMissing.class))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("must be annotated with @PolymorphicValue");
    }

    @Test
    void should_fail_on_duplicate_discriminator_values() {
        assertThatThrownBy(() -> new SealedOutputParser<>(BrokenDuplicate.class))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("Duplicate discriminator value");
    }

    @Test
    void json_schema_contains_all_subtypes() {
        Optional<JsonSchema> jsonSchema = parser.jsonSchema();

        assertThat(jsonSchema).isPresent();
        assertThat(jsonSchema.get().rootElement()).isInstanceOf(JsonAnyOfSchema.class);

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) jsonSchema.get().rootElement();
        assertThat(anyOf.anyOf()).hasSize(2);

        Set<Set<String>> propertySets = anyOf.anyOf().stream()
                .map(JsonObjectSchema.class::cast)
                .map(obj -> obj.properties().keySet())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(propertySets.stream().flatMap(Set::stream)).contains("type", "text", "url");
    }

    @Test
    void format_instructions_mentions_each_variant() {
        String instructions = parser.formatInstructions();

        assertThat(instructions).contains("type").contains("text").contains("image");
    }

    @Test
    void format_instructions_reflect_custom_discriminator() {
        String instructions = new SealedOutputParser<>(Custom.class).formatInstructions();

        assertThat(instructions)
                .contains("discriminator 'kind'")
                .contains("kind=cat")
                .contains("kind=dog");
    }

    @Test
    void should_treat_non_string_discriminator_as_string_value() {
        String json = """
                { "type": 123, "url": "https://example.com" }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(OutputParsingException.class)
                .hasMessageContaining("Unknown discriminator value: 123");
    }
}
