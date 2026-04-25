package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.service.IllegalConfigurationException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolymorphicOutputParserTest {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = TextResponse.class, name = "text"),
        @JsonSubTypes.Type(value = ImageResponse.class, name = "image")
    })
    interface ChatbotResponse {}

    @JsonTypeName("text")
    record TextResponse(String type, String text) implements ChatbotResponse {}

    @JsonTypeName("image")
    record ImageResponse(String type, String url) implements ChatbotResponse {}

    private final PolymorphicOutputParser<ChatbotResponse> parser =
            new PolymorphicOutputParser<>(ChatbotResponse.class);

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = First.class), @JsonSubTypes.Type(value = Second.class)})
    private interface BrokenDuplicate {}

    @JsonTypeName("dup")
    record First(String type) implements BrokenDuplicate {}

    @JsonTypeName("dup")
    record Second(String type) implements BrokenDuplicate {}

    private interface NoSubtypesInterface {}

    sealed interface SealedShape permits SealedCircle, SealedSquare {}

    record SealedCircle(String type, double radius) implements SealedShape {}

    record SealedSquare(String type, double side) implements SealedShape {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = AlphaResponse.class), @JsonSubTypes.Type(value = BetaResponse.class)})
    private interface DefaultName {}

    record AlphaResponse(String type) implements DefaultName {}

    record BetaResponse(String type) implements DefaultName {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = Cat.class), @JsonSubTypes.Type(value = Dog.class)})
    private interface Custom {}

    @JsonTypeName("cat")
    record Cat(String kind, String name) implements Custom {}

    @JsonTypeName("dog")
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
    void should_fail_when_no_subtypes_found() {
        assertThatThrownBy(() -> new PolymorphicOutputParser<>(NoSubtypesInterface.class))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("No subtypes found");
    }

    @Test
    void should_parse_sealed_type_without_annotations() {
        PolymorphicOutputParser<SealedShape> sealedParser = new PolymorphicOutputParser<>(SealedShape.class);

        SealedShape result = sealedParser.parse("""
                { "type": "SealedCircle", "radius": 5.0 }
                """);

        assertThat(result).isInstanceOf(SealedCircle.class);
        assertThat(((SealedCircle) result).radius()).isEqualTo(5.0);
    }

    @Test
    void should_default_discriminator_to_type_for_sealed_interface() {
        PolymorphicOutputParser<SealedShape> sealedParser = new PolymorphicOutputParser<>(SealedShape.class);

        assertThat(sealedParser.formatInstructions()).contains("discriminator 'type'");
    }

    @Test
    void should_fail_on_duplicate_discriminator_values() {
        assertThatThrownBy(() -> new PolymorphicOutputParser<>(BrokenDuplicate.class))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("Duplicate discriminator value");
    }

    @Test
    void should_use_class_name_as_default_discriminator_value() {
        PolymorphicOutputParser<DefaultName> defaultParser = new PolymorphicOutputParser<>(DefaultName.class);

        DefaultName response = defaultParser.parse("""
                { "type": "AlphaResponse" }
                """);

        assertThat(response).isInstanceOf(AlphaResponse.class);
        assertThat(((AlphaResponse) response).type()).isEqualTo("AlphaResponse");
    }

    @Test
    void should_parse_wrapped_value_from_json_schema_mode() {
        String json = """
                { "value": { "type": "image", "url": "https://example.com/img.png" } }
                """;

        ChatbotResponse response = parser.parse(json);

        assertThat(response).isInstanceOf(ImageResponse.class);
        assertThat(((ImageResponse) response).url()).isEqualTo("https://example.com/img.png");
    }

    @Test
    void json_schema_contains_all_subtypes() {
        Optional<JsonSchema> jsonSchema = parser.jsonSchema();

        assertThat(jsonSchema).isPresent();
        assertThat(jsonSchema.get().rootElement()).isInstanceOf(JsonObjectSchema.class);

        JsonObjectSchema root = (JsonObjectSchema) jsonSchema.get().rootElement();
        assertThat(root.properties()).containsKey("value");
        assertThat(root.properties().get("value")).isInstanceOf(JsonAnyOfSchema.class);

        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) root.properties().get("value");
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
        String instructions = new PolymorphicOutputParser<>(Custom.class).formatInstructions();

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

    @Test
    void should_not_use_polymorphic_parser_when_id_is_none() {
        @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
        class SimplePojo {
            public String name;
        }

        OutputParser<?> parser = new DefaultOutputParserFactory().get(SimplePojo.class, null);

        assertThat(parser).isInstanceOf(PojoOutputParser.class);
    }
}
