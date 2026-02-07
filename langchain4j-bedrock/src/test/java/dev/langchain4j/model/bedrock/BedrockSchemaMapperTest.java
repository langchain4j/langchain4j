package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

class BedrockSchemaMapperTest {

    @Test
    void should_convert_string_schema() {
        // given
        JsonSchemaElement stringSchema =
                JsonStringSchema.builder().description("A test string").build();

        // when
        Document result = BedrockSchemaMapper.fromJsonSchemaElementToDocument(stringSchema);

        // then
        assertThat(result.asMap().get("type").asString()).isEqualTo("string");
        assertThat(result.asMap().get("description").asString()).isEqualTo("A test string");
    }

    @Test
    void should_convert_boolean_schema() {
        // given
        JsonSchemaElement boolSchema =
                JsonBooleanSchema.builder().description("A boolean value").build();

        // when
        Document result = BedrockSchemaMapper.fromJsonSchemaElementToDocument(boolSchema);

        // then
        assertThat(result.asMap().get("type").asString()).isEqualTo("boolean");
        assertThat(result.asMap().get("description").asString()).isEqualTo("A boolean value");
    }

    @Test
    void should_convert_number_schema() {
        // given
        JsonSchemaElement numberSchema =
                JsonNumberSchema.builder().description("A number value").build();

        // when
        Document result = BedrockSchemaMapper.fromJsonSchemaElementToDocument(numberSchema);

        // then
        assertThat(result.asMap().get("type").asString()).isEqualTo("number");
        assertThat(result.asMap().get("description").asString()).isEqualTo("A number value");
    }

    @Test
    void should_convert_integer_schema() {
        // given
        JsonSchemaElement integerSchema =
                JsonIntegerSchema.builder().description("An integer value").build();

        // when
        Document result = BedrockSchemaMapper.fromJsonSchemaElementToDocument(integerSchema);

        // then
        assertThat(result.asMap().get("type").asString()).isEqualTo("integer");
        assertThat(result.asMap().get("description").asString()).isEqualTo("An integer value");
    }

    @Test
    void should_convert_enum_schema() {
        // given
        JsonSchemaElement enumSchema = JsonEnumSchema.builder()
                .description("A color")
                .enumValues(Arrays.asList("red", "green", "blue"))
                .build();

        // when
        Document result = BedrockSchemaMapper.fromJsonSchemaElementToDocument(enumSchema);

        // then
        assertThat(result.asMap().get("type").asString()).isEqualTo("string");
        assertThat(result.asMap().get("description").asString()).isEqualTo("A color");

        List<Document> enumList = result.asMap().get("enum").asList();
        assertThat(enumList).hasSize(3);
        assertThat(enumList.get(0).asString()).isEqualTo("red");
        assertThat(enumList.get(1).asString()).isEqualTo("green");
        assertThat(enumList.get(2).asString()).isEqualTo("blue");
    }

    @Test
    void should_convert_null_schema() {
        // given
        JsonSchemaElement nullSchema = new JsonNullSchema();

        // when
        Document result = BedrockSchemaMapper.fromJsonSchemaElementToDocument(nullSchema);

        // then
        assertThat(result.asMap().get("type").asString()).isEqualTo("null");
    }

    @Test
    void should_convert_array_schema() {
        // given
        JsonSchemaElement arraySchema = JsonArraySchema.builder()
                .description("A list of strings")
                .items(JsonStringSchema.builder().build())
                .build();

        // when
        Document result = BedrockSchemaMapper.fromJsonSchemaElementToDocument(arraySchema);

        // then
        assertThat(result.asMap().get("type").asString()).isEqualTo("array");
        assertThat(result.asMap().get("description").asString()).isEqualTo("A list of strings");

        Document items = result.asMap().get("items");
        assertThat(items.asMap().get("type").asString()).isEqualTo("string");
    }

    @Test
    void should_convert_object_schema() {
        // given
        JsonSchemaElement objectSchema = JsonObjectSchema.builder()
                .description("A person")
                .addProperty(
                        "name",
                        JsonStringSchema.builder().description("The name").build())
                .addProperty(
                        "age",
                        JsonIntegerSchema.builder().description("The age").build())
                .required(Arrays.asList("name"))
                .build();

        // when
        Document result = BedrockSchemaMapper.fromJsonSchemaElementToDocument(objectSchema);

        // then
        assertThat(result.asMap().get("type").asString()).isEqualTo("object");
        assertThat(result.asMap().get("description").asString()).isEqualTo("A person");

        Map<String, Document> propsMap = result.asMap().get("properties").asMap();
        assertThat(propsMap).containsKeys("name", "age");
        assertThat(propsMap.get("name").asMap().get("type").asString()).isEqualTo("string");
        assertThat(propsMap.get("age").asMap().get("type").asString()).isEqualTo("integer");

        List<Document> required = result.asMap().get("required").asList();
        assertThat(required).hasSize(1);
        assertThat(required.get(0).asString()).isEqualTo("name");
    }

    @Test
    void should_convert_nested_object_schema() {
        // given
        JsonSchemaElement addressSchema = JsonObjectSchema.builder()
                .description("An address")
                .addStringProperty("street")
                .addStringProperty("city")
                .build();

        JsonSchemaElement personSchema = JsonObjectSchema.builder()
                .description("A person with address")
                .addStringProperty("name")
                .addProperty("address", addressSchema)
                .build();

        // when
        Document result = BedrockSchemaMapper.fromJsonSchemaElementToDocument(personSchema);

        // then
        assertThat(result.asMap().get("type").asString()).isEqualTo("object");

        Map<String, Document> propsMap = result.asMap().get("properties").asMap();
        assertThat(propsMap).containsKeys("name", "address");

        Document addressDoc = propsMap.get("address");
        assertThat(addressDoc.asMap().get("type").asString()).isEqualTo("object");
        assertThat(addressDoc.asMap().get("properties").asMap()).containsKeys("street", "city");
    }

    @Test
    void should_convert_json_schema_wrapper() {
        // given
        JsonSchema schema = JsonSchema.builder()
                .name("Book")
                .rootElement(
                        JsonObjectSchema.builder().addStringProperty("title").build())
                .build();

        // when
        Document result = BedrockSchemaMapper.fromJsonSchemaToDocument(schema);

        // then
        assertThat(result.asMap().get("type").asString()).isEqualTo("object");
        assertThat(result.asMap().get("properties").asMap()).containsKey("title");
    }

    @Test
    void should_return_null_for_null_input() {
        // when/then
        assertThat(BedrockSchemaMapper.fromJsonSchemaToDocument(null)).isNull();
        assertThat(BedrockSchemaMapper.fromJsonSchemaElementToDocument(null)).isNull();
    }
}
