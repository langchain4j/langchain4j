package dev.langchain4j.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolSpecificationJsonTest {

    @Test
    void fromJson_should_reject_null() {
        assertThatThrownBy(() -> ToolSpecification.fromJson(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_round_trip_with_parameters_and_metadata() {
        ToolSpecification original = ToolSpecification.builder()
                .name("get_weather")
                .description("Gets the weather for a location")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("location", "city name")
                        .addEnumProperty("unit", java.util.List.of("CELSIUS", "FAHRENHEIT"))
                        .required("location")
                        .build())
                .addMetadata("cache", true)
                .build();

        String json = original.toJson();
        ToolSpecification restored = ToolSpecification.fromJson(json);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void should_round_trip_without_parameters() {
        ToolSpecification original = ToolSpecification.builder()
                .name("get_time")
                .description("Gets the current time")
                .build();

        String json = original.toJson();
        ToolSpecification restored = ToolSpecification.fromJson(json);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void should_round_trip_name_only() {
        ToolSpecification original = ToolSpecification.builder().name("ping").build();

        String json = original.toJson();
        ToolSpecification restored = ToolSpecification.fromJson(json);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void should_round_trip_with_strict_true() {
        ToolSpecification original = ToolSpecification.builder()
                .name("strict_tool")
                .description("A tool with strict enforcement")
                .strict(true)
                .build();

        String json = original.toJson();
        assertThat(json).contains("\"strict\":true");

        ToolSpecification restored = ToolSpecification.fromJson(json);
        assertThat(restored).isEqualTo(original);
        assertThat(restored.strict()).isTrue();
    }

    @Test
    void should_round_trip_with_strict_false() {
        ToolSpecification original = ToolSpecification.builder()
                .name("non_strict_tool")
                .strict(false)
                .build();

        String json = original.toJson();
        assertThat(json).contains("\"strict\":false");

        ToolSpecification restored = ToolSpecification.fromJson(json);
        assertThat(restored).isEqualTo(original);
        assertThat(restored.strict()).isFalse();
    }

    @Test
    void should_round_trip_with_strict_null() {
        ToolSpecification original =
                ToolSpecification.builder().name("default_tool").build();

        String json = original.toJson();
        assertThat(json).doesNotContain("strict");

        ToolSpecification restored = ToolSpecification.fromJson(json);
        assertThat(restored).isEqualTo(original);
        assertThat(restored.strict()).isNull();
    }

    @Test
    void should_reject_non_boolean_strict() {
        String json = "{\"name\":\"test\",\"strict\":\"yes\"}";
        assertThatThrownBy(() -> ToolSpecification.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strict");
    }

    @Test
    void should_reject_non_object_parameters() {
        String json = "{\"name\":\"test\",\"parameters\":{\"type\":\"string\"}}";
        assertThatThrownBy(() -> ToolSpecification.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parameters");
    }

    @Test
    void should_reject_non_object_metadata() {
        String json = "{\"name\":\"test\",\"metadata\":\"bad\"}";
        assertThatThrownBy(() -> ToolSpecification.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void should_reject_non_string_fields() {
        assertThatThrownBy(() -> ToolSpecification.fromJson("{\"name\":123}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> ToolSpecification.fromJson("{\"name\":\"test\",\"description\":123}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void should_reject_parameters_with_extra_schema_keywords() {
        // Standard JSON Schema root keywords like $schema/title make the object
        // fall back to JsonRawSchema, which is not a valid ToolSpecification parameters type.
        String json =
                "{\"name\":\"test\",\"parameters\":{\"type\":\"object\",\"$schema\":\"http://json-schema.org/draft-07/schema#\"}}";
        assertThatThrownBy(() -> ToolSpecification.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parameters");
    }

    @Test
    void should_produce_stable_json_for_complex_tool_specification() {
        // This test guards the serialization format as a backward-compatibility contract.
        // Tool specifications may be persisted and later deserialized by a newer version of LC4j.
        // If this test fails, the serialization format has changed in a breaking way.

        ToolSpecification original = ToolSpecification.builder()
                .name("complex_tool")
                .description("A tool exercising every schema element type")
                .parameters(JsonObjectSchema.builder()
                        .description("root params")
                        .addProperty(
                                "stringProp",
                                JsonStringSchema.builder()
                                        .description("a string")
                                        .build())
                        .addProperty(
                                "intProp",
                                JsonIntegerSchema.builder()
                                        .description("an integer")
                                        .build())
                        .addProperty(
                                "numProp",
                                JsonNumberSchema.builder()
                                        .description("a number")
                                        .build())
                        .addProperty(
                                "boolProp",
                                JsonBooleanSchema.builder()
                                        .description("a boolean")
                                        .build())
                        .addProperty("nullProp", new JsonNullSchema())
                        .addProperty(
                                "enumProp",
                                JsonEnumSchema.builder()
                                        .description("a color")
                                        .enumValues("RED", "GREEN", "BLUE")
                                        .build())
                        .addProperty(
                                "arrayProp",
                                JsonArraySchema.builder()
                                        .description("a list of integers")
                                        .items(new JsonIntegerSchema())
                                        .build())
                        .addProperty(
                                "objectProp",
                                JsonObjectSchema.builder()
                                        .description("nested object")
                                        .addProperty(
                                                "inner",
                                                JsonStringSchema.builder()
                                                        .description("inner field")
                                                        .build())
                                        .required("inner")
                                        .additionalProperties(false)
                                        .build())
                        .addProperty(
                                "anyOfProp",
                                JsonAnyOfSchema.builder()
                                        .description("string or number")
                                        .anyOf(new JsonStringSchema(), new JsonNumberSchema())
                                        .build())
                        .addProperty(
                                "refProp",
                                JsonReferenceSchema.builder()
                                        .reference("SharedDef")
                                        .build())
                        .definitions(Map.of(
                                "SharedDef",
                                JsonObjectSchema.builder()
                                        .addProperty(
                                                "id",
                                                JsonIntegerSchema.builder()
                                                        .description("identifier")
                                                        .build())
                                        .required("id")
                                        .build()))
                        .required("stringProp", "enumProp")
                        .build())
                .addMetadata("cache", true)
                .addMetadata("version", "2.0")
                .build();

        String json = original.toJson();

        String expectedJson = """
                {\
                "name":"complex_tool",\
                "description":"A tool exercising every schema element type",\
                "parameters":{\
                "type":"object",\
                "description":"root params",\
                "properties":{\
                "stringProp":{"type":"string","description":"a string"},\
                "intProp":{"type":"integer","description":"an integer"},\
                "numProp":{"type":"number","description":"a number"},\
                "boolProp":{"type":"boolean","description":"a boolean"},\
                "nullProp":{"type":"null"},\
                "enumProp":{"type":"string","description":"a color","enum":["RED","GREEN","BLUE"]},\
                "arrayProp":{"type":"array","description":"a list of integers","items":{"type":"integer"}},\
                "objectProp":{"type":"object","description":"nested object","properties":{"inner":{"type":"string","description":"inner field"}},"required":["inner"],"additionalProperties":false},\
                "anyOfProp":{"description":"string or number","anyOf":[{"type":"string"},{"type":"number"}]},\
                "refProp":{"$ref":"#/$defs/SharedDef"}},\
                "required":["stringProp","enumProp"],\
                "$defs":{"SharedDef":{"type":"object","properties":{"id":{"type":"integer","description":"identifier"}},"required":["id"]}}},\
                "metadata":{"cache":true,"version":"2.0"}\
                }""";

        assertThat(json).isEqualTo(expectedJson);

        ToolSpecification restored = ToolSpecification.fromJson(json);
        assertThat(restored).isEqualTo(original);
    }
}
