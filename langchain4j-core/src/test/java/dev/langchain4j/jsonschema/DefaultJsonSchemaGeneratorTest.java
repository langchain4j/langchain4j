package dev.langchain4j.jsonschema;

import static dev.langchain4j.TestReflectUtil.getMethodByName;
import static dev.langchain4j.internal.Utils.mapOf;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.toMap;
import static dev.langchain4j.jsonschema.DefaultJsonSchemaGenerator.removeNulls;
import static java.util.Collections.singletonMap;

import dev.langchain4j.TestReflectUtil;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import dev.langchain4j.exception.JsonSchemaGenerationException;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;

public class DefaultJsonSchemaGeneratorTest implements WithAssertions {

    public static final String PARAM_STRING_DESC = "String param";
    public static final String PARAM_INTEGERS_DESC = "Integers param";
    public static final String PARAM_WILD_LIST_DESC = "Wild list param";
    public static final String PARAM_FLOAT_SET_DESC = "Float set param";
    public static final String PARAM_NEST_INTEGERS_DESC = "Nested integers param";
    public static final String PARAM_MAPPING_DESC = "Mapping param";
    public static final String PARAM_WILD_MAPPING_DESC = "Wild mapping param";
    public static final String PARAM_MAPPING_DOUBLES_DESC = "Mapping nested param";
    public static final JsonSchemaServiceFactories.Service DEFAULT_SERVICE =
            JsonSchemaServiceFactories.loadService();

    @Test
    public void test_removeNulls() {
        assertThat(removeNulls(null, JsonSchemaProperty.STRING, null))
                .containsExactly(JsonSchemaProperty.STRING);
    }

    @SuppressWarnings("unused")
    public static class DummyToolkit {

        @Tool
        public void dummyTool(
                @P(PARAM_STRING_DESC) String string,
                @P(PARAM_INTEGERS_DESC) List<Integer> integers,
                @SuppressWarnings("rawtypes") @P(PARAM_WILD_LIST_DESC) List wildList,
                @P(PARAM_FLOAT_SET_DESC) Set<Float> floatSet,
                @P(PARAM_NEST_INTEGERS_DESC) List<List<Integer>> nestedIntegers,
                @P(PARAM_MAPPING_DESC) Map<String, Integer> mapping,
                @SuppressWarnings("rawtypes") @P(PARAM_WILD_MAPPING_DESC) Map wildMapping,
                @P(PARAM_MAPPING_DOUBLES_DESC) Map<String, List<Double>> mappingDoubles,
                double notAnnotated) {
            // do nothing
        }
    }

    @Test
    public void test_generator_withBuiltInTypes() throws JsonSchemaGenerationException {
        DefaultJsonSchemaGenerator generator = new DefaultJsonSchemaGenerator();

        assertThat(generator.generate(String.class))
                .returns(removeNulls(JsonSchemaProperty.STRING), JsonSchema::getProperties);

        for (Class<?> clazz : new Class<?>[] {Boolean.class, boolean.class}) {
            assertThat(generator.generate(clazz))
                    .returns(removeNulls(JsonSchemaProperty.BOOLEAN), JsonSchema::getProperties);
        }

        for (Class<?> clazz :
                new Class<?>[] {Float.class, float.class, Double.class, double.class}) {
            assertThat(generator.generate(clazz))
                    .returns(removeNulls(JsonSchemaProperty.NUMBER), JsonSchema::getProperties);
        }

        for (Class<?> clazz :
                new Class<?>[] {
                    Integer.class, int.class, Long.class, long.class, Short.class, short.class
                }) {
            assertThat(generator.generate(clazz))
                    .as("Class: " + clazz.getName())
                    .returns(removeNulls(JsonSchemaProperty.INTEGER), JsonSchema::getProperties);
        }

        assertThat(generator.generate(Void.class))
                .returns(removeNulls(JsonSchemaProperty.OBJECT), JsonSchema::getProperties);

        assertThat(generator.generate(Integer[].class))
                .returns(
                        removeNulls(
                                JsonSchemaProperty.ARRAY,
                                JsonSchemaProperty.items(JsonSchemaProperty.INTEGER)),
                        JsonSchema::getProperties);

        for (Type type :
                new Type[] {
                    new TestReflectUtil.TypeTrait<List<Integer>>() {}.getType(),
                    new TestReflectUtil.TypeTrait<Set<Integer>>() {}.getType(),
                    new TestReflectUtil.TypeTrait<Collection<Integer>>() {}.getType()
                }) {
            assertThat(generator.generate(type))
                    .returns(
                            removeNulls(
                                    JsonSchemaProperty.ARRAY,
                                    JsonSchemaProperty.items(JsonSchemaProperty.INTEGER)),
                            JsonSchema::getProperties);
        }
    }

    @Test
    public void test_toJsonSchemaProperties() throws JsonSchemaGenerationException {
        Method method = getMethodByName(DummyToolkit.class, "dummyTool");

        Map<String, List<JsonSchemaProperty>> properties = new HashMap<>();
        for (Parameter parameter : method.getParameters()) {
            P annotation = parameter.getAnnotation(P.class);
            JsonSchema jsonSchema =
                    DEFAULT_SERVICE
                            .generate(parameter.getParameterizedType())
                            .addDescription(annotation == null ? null : annotation.value());
            properties.put(parameter.getName(), jsonSchema.getProperties());
        }

        Map<String, Map<String, Object>> propertiesMap = new HashMap<>();
        for (Map.Entry<String, List<JsonSchemaProperty>> entry : properties.entrySet()) {
            propertiesMap.put(entry.getKey(), toMap(entry.getValue()));
        }

        assertThat(propertiesMap)
                .hasSize(method.getParameterCount())
                .containsEntry(
                        "arg0",
                        mapOf(entry("type", "string"), entry("description", PARAM_STRING_DESC)))
                .containsEntry(
                        "arg1",
                        mapOf(
                                entry("type", "array"),
                                entry("items", singletonMap("type", "integer")),
                                entry("description", PARAM_INTEGERS_DESC)))
                .containsEntry(
                        "arg2",
                        mapOf(
                                entry("type", "array"),
                                entry("items", singletonMap("type", "object")),
                                entry("description", PARAM_WILD_LIST_DESC)))
                .containsEntry(
                        "arg3",
                        mapOf(
                                entry("type", "array"),
                                entry("items", singletonMap("type", "number")),
                                entry("description", PARAM_FLOAT_SET_DESC)))
                .containsEntry(
                        "arg4",
                        mapOf(
                                entry("type", "array"),
                                entry(
                                        "items",
                                        mapOf(
                                                entry("type", "array"),
                                                entry("items", singletonMap("type", "integer")))),
                                entry("description", PARAM_NEST_INTEGERS_DESC)))
                .containsEntry(
                        "arg5",
                        mapOf(
                                entry("type", "object"),
                                entry("additionalProperties", singletonMap("type", "integer")),
                                entry("description", PARAM_MAPPING_DESC)))
                .containsEntry(
                        "arg6",
                        mapOf(
                                entry("type", "object"),
                                entry("description", PARAM_WILD_MAPPING_DESC)))
                .containsEntry(
                        "arg7",
                        mapOf(
                                entry("type", "object"),
                                entry(
                                        "additionalProperties",
                                        mapOf(
                                                entry("type", "array"),
                                                entry("items", singletonMap("type", "number")))),
                                entry("description", PARAM_MAPPING_DOUBLES_DESC)))
                .containsEntry("arg8", singletonMap("type", "number"));
    }

    @SuppressWarnings("unused")
    public static class CustomType {
        String stringField;
        List<Integer> itemsField;
    }

    @Test
    public void test_generator_withCustomType() {
        DefaultJsonSchemaGenerator generator = new DefaultJsonSchemaGenerator();
        assertThatCode(() -> generator.generate(CustomType.class))
                .isInstanceOf(JsonSchemaGenerationException.class)
                .hasMessageMatching(
                        "Unsupported Custom-type tool parameter:.*"
                                + "add `langchain4j-jsonschema-service-\\*' dependency");
    }
}
