package dev.langchain4j.agent.tool;

import static dev.langchain4j.TestReflectUtil.getMethodByName;
import static dev.langchain4j.internal.Utils.mapOf;

import dev.langchain4j.exception.JsonSchemaGenerationException;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

class ToolJsonSchemaTest implements WithAssertions {
    private static final String toolDescription = "func_description";
    ToolJsonSchemas toolJsonSchemas = new ToolJsonSchemas();

    @SuppressWarnings("unused")
    @Tool(toolDescription)
    void func(
            @ToolMemoryId String memoryId,
            String arg1,
            @P("Description of arg2") int arg2,
            @P("") Integer arg3) {}

    @Test
    public void test_toToolSpecification() throws JsonSchemaGenerationException {
        Method method = getMethodByName(this.getClass(), "func");

        ToolJsonSchema schema = toolJsonSchemas.generateToolJsonSchema(method);
        ToolSpecification ts = schema.toToolSpecification();

        Map<String, Map<String, Object>> properties =
                mapOf(
                        entry("arg1", mapOf(entry("type", "string"))),
                        entry(
                                "arg2",
                                mapOf(
                                        entry("type", "integer"),
                                        entry("description", "Description of arg2"))),
                        entry("arg3", mapOf(entry("type", "integer"))));

        assertThat(ts.name()).isEqualTo("func");
        assertThat(ts.description()).isEqualTo(toolDescription);
        assertThat(ts.parameters())
                .isEqualTo(
                        ToolParameters.builder()
                                .type("object")
                                .properties(properties)
                                .required(new ArrayList<>(properties.keySet()))
                                .build());
    }
}
