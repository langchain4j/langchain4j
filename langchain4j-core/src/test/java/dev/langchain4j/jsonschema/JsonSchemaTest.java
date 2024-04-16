package dev.langchain4j.jsonschema;

import dev.langchain4j.agent.tool.JsonSchemaProperty;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class JsonSchemaTest implements WithAssertions {
    @Test
    public void test_addDescription_when_having_type_desc() {
        final String typeDescription = "Type desc";
        final String paramDescription = "Param desc";

        JsonSchema schema =
                new JsonSchema(
                        Arrays.asList(
                                JsonSchemaProperty.OBJECT,
                                JsonSchemaProperty.description(typeDescription),
                                JsonSchemaProperty.from("properties", Arrays.asList(0, 1, 2))),
                        Object.class);

        assertThat(schema.addDescription(paramDescription))
                .returns(
                        Arrays.asList(
                                JsonSchemaProperty.OBJECT,
                                JsonSchemaProperty.description(
                                        paramDescription + "\n" + typeDescription),
                                JsonSchemaProperty.from("properties", Arrays.asList(0, 1, 2))),
                        JsonSchema::getProperties);
    }
    @Test
    public void test_addDescription_when_no_type_desc() {
        final String paramDescription = "Param desc";

        JsonSchema schema =
                new JsonSchema(
                        Arrays.asList(
                                JsonSchemaProperty.OBJECT,
                                JsonSchemaProperty.from("properties", Arrays.asList(0, 1, 2))),
                        Object.class);

        assertThat(schema.addDescription(paramDescription))
                .returns(
                        Arrays.asList(
                                JsonSchemaProperty.OBJECT,
                                JsonSchemaProperty.description(paramDescription),
                                JsonSchemaProperty.from("properties", Arrays.asList(0, 1, 2))),
                        JsonSchema::getProperties);
    }
}
