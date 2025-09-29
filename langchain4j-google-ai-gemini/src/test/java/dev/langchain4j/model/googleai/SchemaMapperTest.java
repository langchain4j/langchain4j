package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SchemaMapperTest {
    @Test
    public void should_map_string_schema() {
        // given
        JsonStringSchema stringSchema =
                JsonStringSchema.builder().description("The name of the tool").build();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(stringSchema);

        // then
        assertThat(result.getType()).isEqualTo(GeminiType.STRING);
        assertThat(result.getDescription()).isEqualTo("The name of the tool");
    }

    @Test
    public void should_map_boolean_schema() {
        // given
        JsonBooleanSchema boolSchema = JsonBooleanSchema.builder()
                .description("Whether the tool is enabled")
                .build();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(boolSchema);

        // then
        assertThat(result.getType()).isEqualTo(GeminiType.BOOLEAN);
        assertThat(result.getDescription()).isEqualTo("Whether the tool is enabled");
    }

    @Test
    public void should_map_number_schema() {
        // given
        JsonNumberSchema numberSchema = JsonNumberSchema.builder()
                .description("The confidence threshold for tool execution")
                .build();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(numberSchema);

        // then
        assertThat(result.getType()).isEqualTo(GeminiType.NUMBER);
        assertThat(result.getDescription()).isEqualTo("The confidence threshold for tool execution");
    }

    @Test
    public void should_map_integer_schema() {
        // given
        JsonIntegerSchema integerSchema = JsonIntegerSchema.builder()
                .description("Maximum number of tool calls allowed")
                .build();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(integerSchema);

        // then
        assertThat(result.getType()).isEqualTo(GeminiType.INTEGER);
        assertThat(result.getDescription()).isEqualTo("Maximum number of tool calls allowed");
    }

    @Test
    public void should_map_enum_schema() {
        // given
        List<String> values = Arrays.asList("search", "calculate", "retrieve");
        JsonEnumSchema enumSchema = JsonEnumSchema.builder()
                .description("Type of tool operation")
                .enumValues(values)
                .build();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(enumSchema);

        // then
        assertThat(result.getType()).isEqualTo(GeminiType.STRING);
        assertThat(result.getDescription()).isEqualTo("Type of tool operation");
        assertThat(result.getEnumeration()).containsExactlyElementsOf(values);
    }

    @Test
    public void should_map_array_schema() {
        // given
        JsonArraySchema arraySchema = JsonArraySchema.builder()
                .description("List of supported file formats")
                .items(JsonStringSchema.builder()
                        .description("File format extension")
                        .build())
                .build();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(arraySchema);

        // then
        assertThat(result.getType()).isEqualTo(GeminiType.ARRAY);
        assertThat(result.getDescription()).isEqualTo("List of supported file formats");
        assertThat(result.getItems()).isNotNull();
        assertThat(result.getItems().getType()).isEqualTo(GeminiType.STRING);
        assertThat(result.getItems().getDescription()).isEqualTo("File format extension");
    }

    @Test
    public void should_map_object_schema() {
        // given
        Map<String, JsonSchemaElement> properties = new HashMap<>();
        properties.put(
                "query",
                JsonStringSchema.builder().description("Search query string").build());
        properties.put(
                "limit",
                JsonIntegerSchema.builder()
                        .description("Maximum number of results")
                        .build());

        JsonObjectSchema objectSchema = JsonObjectSchema.builder()
                .description("Search tool parameters")
                .addProperties(properties)
                .required("query")
                .build();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(objectSchema);

        // then
        assertThat(result.getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(result.getDescription()).isEqualTo("Search tool parameters");
        assertThat(result.getProperties()).hasSize(2);
        assertThat(result.getProperties().get("query").getType()).isEqualTo(GeminiType.STRING);
        assertThat(result.getProperties().get("limit").getType()).isEqualTo(GeminiType.INTEGER);
        assertThat(result.getRequired()).containsExactly("query");
    }

    @Test
    public void should_map_nested_object_schema() {
        // given
        JsonObjectSchema filterSchema = JsonObjectSchema.builder()
                .description("Search filters")
                .addStringProperty("category", "Category to filter by")
                .addStringProperty("domain", "Domain to limit search to")
                .build();

        JsonObjectSchema searchSchema = JsonObjectSchema.builder()
                .description("Web search tool")
                .addStringProperty("query", "Search query text")
                .addProperty("filters", filterSchema)
                .build();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(searchSchema);

        // then
        assertThat(result.getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(result.getProperties()).hasSize(2);
        assertThat(result.getProperties().get("filters").getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(result.getProperties().get("filters").getProperties()).hasSize(2);
    }

    @Test
    public void should_map_complex_schema() {
        // given
        JsonSchema schema = JsonSchema.builder()
                .name("WeatherTool")
                .rootElement(JsonObjectSchema.builder()
                        .description("Weather forecast tool")
                        .addStringProperty("location", "City or address to check weather for")
                        .addIntegerProperty("days", "Number of days to forecast")
                        .addProperty(
                                "units",
                                JsonEnumSchema.builder()
                                        .description("Temperature units")
                                        .enumValues("celsius", "fahrenheit")
                                        .build())
                        .addProperty(
                                "features",
                                JsonArraySchema.builder()
                                        .description("Weather data features to include")
                                        .items(JsonStringSchema.builder().build())
                                        .build())
                        .addProperty(
                                "options",
                                JsonObjectSchema.builder()
                                        .description("Additional query options")
                                        .addBooleanProperty("includeHourly", "Include hourly breakdown")
                                        .addBooleanProperty("includeAlerts", "Include weather alerts")
                                        .required("includeHourly")
                                        .build())
                        .required("location", "days")
                        .build())
                .build();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(schema);

        // then
        assertThat(result.getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(result.getProperties()).hasSize(5);
        assertThat(result.getRequired()).containsExactly("location", "days");

        // Check features array
        GeminiSchema features = result.getProperties().get("features");
        assertThat(features.getType()).isEqualTo(GeminiType.ARRAY);
        assertThat(features.getItems().getType()).isEqualTo(GeminiType.STRING);

        // Check options object
        GeminiSchema options = result.getProperties().get("options");
        assertThat(options.getType()).isEqualTo(GeminiType.OBJECT);
        assertThat(options.getProperties()).hasSize(2);
        assertThat(options.getRequired()).containsExactly("includeHourly");
    }

    @Test
    public void should_handle_anyof_schema() {
        // given
        JsonSchemaElement anyOfSchema = JsonAnyOfSchema.builder()
                .description("A value that can be either a string or a number")
                .anyOf(
                        JsonStringSchema.builder().build(),
                        JsonNumberSchema.builder().build())
                .build();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(anyOfSchema);

        // then
        assertThat(result.getDescription()).isEqualTo("A value that can be either a string or a number");
        assertThat(result.getAnyOf()).hasSize(2);
        assertThat(result.getAnyOf().get(0).getType()).isEqualTo(GeminiType.STRING);
        assertThat(result.getAnyOf().get(1).getType()).isEqualTo(GeminiType.NUMBER);
    }

    @Test
    public void should_handle_null_schema() {
        // given
        JsonSchemaElement nullSchema = new JsonNullSchema();

        // when
        GeminiSchema result = SchemaMapper.fromJsonSchemaToGSchema(nullSchema);

        // then
        assertThat(result.getType()).isEqualTo(GeminiType.NULL);
    }

    @Test
    public void should_throw_exception_for_unsupported_schema_type() {
        // given
        JsonSchemaElement unsupportedSchema =
                new JsonRawSchema.Builder().schema("{ \"type\": \"string\" }").build();

        // when/then
        assertThrows(IllegalArgumentException.class, () -> {
            SchemaMapper.fromJsonSchemaToGSchema(unsupportedSchema);
        });
    }
}
