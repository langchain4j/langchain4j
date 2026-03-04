package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleGenAiToolMapperTest {

    // --- convertToGoogleTool ---

    @Test
    void should_convert_tool_specification_to_google_tool() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather info")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city", "city name")
                        .build())
                .build();

        Tool tool = GoogleGenAiToolMapper.convertToGoogleTool(spec);

        assertThat(tool.functionDeclarations().get()).hasSize(1);
        FunctionDeclaration fd = tool.functionDeclarations().get().get(0);
        assertThat(fd.name().get()).isEqualTo("getWeather");
        assertThat(fd.description().get()).isEqualTo("Get weather info");
    }

    @Test
    void should_convert_tool_with_blank_description() {
        ToolSpecification spec =
                ToolSpecification.builder().name("doSomething").description("").build();

        Tool tool = GoogleGenAiToolMapper.convertToGoogleTool(spec);

        assertThat(tool.functionDeclarations().get().get(0).description().get()).isEqualTo("");
    }

    @Test
    void should_convert_tool_with_null_description() {
        ToolSpecification spec = ToolSpecification.builder().name("doSomething").build();

        Tool tool = GoogleGenAiToolMapper.convertToGoogleTool(spec);

        assertThat(tool.functionDeclarations().get().get(0).description().get()).isEqualTo("");
    }

    @Test
    void should_convert_tool_with_non_blank_description() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("doSomething")
                .description("A real description")
                .build();

        Tool tool = GoogleGenAiToolMapper.convertToGoogleTool(spec);

        assertThat(tool.functionDeclarations().get().get(0).description().get()).isEqualTo("A real description");
    }

    // --- convertToGoogleFunction ---

    @Test
    void should_convert_tool_specification_to_google_function() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city", "city name")
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);

        assertThat(fd.name().get()).isEqualTo("getWeather");
        assertThat(fd.description().get()).isEqualTo("Get weather");
        assertThat(fd.parameters().get().type().get()).hasToString("OBJECT");
    }

    @Test
    void should_convert_function_with_null_description() {
        ToolSpecification spec = ToolSpecification.builder().name("doSomething").build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);

        assertThat(fd.description().get()).isEqualTo("");
    }

    @Test
    void should_convert_function_with_null_parameters() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("doSomething")
                .description("desc")
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);

        assertThat(fd.parameters().isPresent()).isFalse();
    }

    // --- Schema conversions ---

    @Test
    void should_convert_object_schema_with_properties() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("name", "user name")
                        .addIntegerProperty("age", "user age")
                        .required(List.of("name"))
                        .description("user object")
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema schema = fd.parameters().get();

        assertThat(schema.type().get()).hasToString("OBJECT");
        assertThat(schema.properties().get()).containsKeys("name", "age");
        assertThat(schema.required().get()).containsExactly("name");
        assertThat(schema.description().get()).isEqualTo("user object");
    }

    @Test
    void should_convert_object_schema_with_null_properties() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder().build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema schema = fd.parameters().get();

        assertThat(schema.type().get()).hasToString("OBJECT");
    }

    @Test
    void should_convert_string_schema() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "name",
                                JsonStringSchema.builder().description("a name").build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema nameSchema = fd.parameters().get().properties().get().get("name");

        assertThat(nameSchema.type().get()).hasToString("STRING");
        assertThat(nameSchema.description().get()).isEqualTo("a name");
    }

    @Test
    void should_convert_string_schema_with_null_description() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "name", JsonStringSchema.builder().build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema nameSchema = fd.parameters().get().properties().get().get("name");

        assertThat(nameSchema.description().get()).isEqualTo("");
    }

    @Test
    void should_convert_enum_schema() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "color",
                                JsonEnumSchema.builder()
                                        .enumValues(List.of("RED", "GREEN", "BLUE"))
                                        .description("a color")
                                        .build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema colorSchema = fd.parameters().get().properties().get().get("color");

        assertThat(colorSchema.type().get()).hasToString("STRING");
        assertThat(colorSchema.format().get()).isEqualTo("enum");
        assertThat(colorSchema.enum_().get()).containsExactly("RED", "GREEN", "BLUE");
    }

    @Test
    void should_convert_enum_schema_with_null_description() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "color",
                                JsonEnumSchema.builder()
                                        .enumValues(List.of("RED"))
                                        .build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema colorSchema = fd.parameters().get().properties().get().get("color");

        assertThat(colorSchema.description().get()).isEqualTo("");
    }

    @Test
    void should_convert_integer_schema() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "count",
                                JsonIntegerSchema.builder()
                                        .description("a count")
                                        .build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema countSchema = fd.parameters().get().properties().get().get("count");

        assertThat(countSchema.type().get()).hasToString("INTEGER");
        assertThat(countSchema.description().get()).isEqualTo("a count");
    }

    @Test
    void should_convert_integer_schema_with_null_description() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "count", JsonIntegerSchema.builder().build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);

        assertThat(fd.parameters()
                        .get()
                        .properties()
                        .get()
                        .get("count")
                        .description()
                        .get())
                .isEqualTo("");
    }

    @Test
    void should_convert_number_schema() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "price",
                                JsonNumberSchema.builder()
                                        .description("a price")
                                        .build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema priceSchema = fd.parameters().get().properties().get().get("price");

        assertThat(priceSchema.type().get()).hasToString("NUMBER");
        assertThat(priceSchema.description().get()).isEqualTo("a price");
    }

    @Test
    void should_convert_number_schema_with_null_description() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "price", JsonNumberSchema.builder().build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);

        assertThat(fd.parameters()
                        .get()
                        .properties()
                        .get()
                        .get("price")
                        .description()
                        .get())
                .isEqualTo("");
    }

    @Test
    void should_convert_boolean_schema() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "active",
                                JsonBooleanSchema.builder()
                                        .description("is active")
                                        .build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema activeSchema = fd.parameters().get().properties().get().get("active");

        assertThat(activeSchema.type().get()).hasToString("BOOLEAN");
        assertThat(activeSchema.description().get()).isEqualTo("is active");
    }

    @Test
    void should_convert_boolean_schema_with_null_description() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "active", JsonBooleanSchema.builder().build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);

        assertThat(fd.parameters()
                        .get()
                        .properties()
                        .get()
                        .get("active")
                        .description()
                        .get())
                .isEqualTo("");
    }

    @Test
    void should_convert_array_schema() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "tags",
                                JsonArraySchema.builder()
                                        .items(JsonStringSchema.builder().build())
                                        .description("list of tags")
                                        .build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema tagsSchema = fd.parameters().get().properties().get().get("tags");

        assertThat(tagsSchema.type().get()).hasToString("ARRAY");
        assertThat(tagsSchema.items().get().type().get()).hasToString("STRING");
        assertThat(tagsSchema.description().get()).isEqualTo("list of tags");
    }

    @Test
    void should_convert_array_schema_with_null_description() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "tags",
                                JsonArraySchema.builder()
                                        .items(JsonStringSchema.builder().build())
                                        .build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);

        assertThat(fd.parameters()
                        .get()
                        .properties()
                        .get()
                        .get("tags")
                        .description()
                        .get())
                .isEqualTo("");
    }

    @Test
    void should_throw_for_unknown_schema_type() {
        JsonSchemaElement unknownElement = new JsonSchemaElement() {
            @Override
            public String description() {
                return "unknown";
            }
        };

        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of("unknown", unknownElement))
                        .build())
                .build();

        assertThatThrownBy(() -> GoogleGenAiToolMapper.convertToGoogleFunction(spec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown schema type");
    }

    @Test
    void should_convert_nested_object_schema() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("test")
                .description("test")
                .parameters(JsonObjectSchema.builder()
                        .addProperties(java.util.Map.of(
                                "address",
                                JsonObjectSchema.builder()
                                        .addStringProperty("street", "street name")
                                        .addStringProperty("city", "city name")
                                        .required(List.of("street"))
                                        .build()))
                        .build())
                .build();

        FunctionDeclaration fd = GoogleGenAiToolMapper.convertToGoogleFunction(spec);
        Schema addressSchema = fd.parameters().get().properties().get().get("address");

        assertThat(addressSchema.type().get()).hasToString("OBJECT");
        assertThat(addressSchema.properties().get()).containsKeys("street", "city");
    }
}
