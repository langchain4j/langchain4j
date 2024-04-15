package dev.langchain4j.jsonschema;

import static dev.langchain4j.internal.Utils.mapOf;
import static dev.langchain4j.jsonschema.DefaultJsonSchemaGenerator.removeNulls;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;

import dev.langchain4j.agent.tool.JsonSchemaProperty;

import dev.langchain4j.exception.JsonSchemaGenerationException;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.*;

public class VictoolsJsonSchemaGeneratorTest implements WithAssertions {

    @Test
    public void test_generator_withBuiltInTypes() throws JsonSchemaGenerationException {
        JsonSchemaService.JsonSchemaGenerator generator = getJsonSchemaGenerator();

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
                    new TypeReference<List<Integer>>() {}.getType(),
                    new TypeReference<Set<Integer>>() {}.getType(),
                    new TypeReference<Collection<Integer>>() {}.getType(),
                }) {
            assertThat(generator.generate(type))
                    .returns(
                            removeNulls(
                                    JsonSchemaProperty.ARRAY,
                                    JsonSchemaProperty.items(JsonSchemaProperty.INTEGER)),
                            JsonSchema::getProperties);
        }

        assertThat(generator.generate(new TypeReference<Map<String, Integer>>() {}.getType()))
                .returns(
                        removeNulls(
                                JsonSchemaProperty.OBJECT,
                                JsonSchemaProperty.additionalProperties(
                                        singletonList(JsonSchemaProperty.INTEGER))),
                        JsonSchema::getProperties);
    }

    private static final String CUSTOM_TYPE_DESCRIPTION = "Custom type description";

    @SuppressWarnings("unused")
    @JsonClassDescription(CUSTOM_TYPE_DESCRIPTION)
    static class CustomType {
        @JsonProperty(required = true)
        private String stringField;

        // this field will not be required
        @JsonProperty(/*required = false */ ) private boolean boolField;

        @JsonProperty(required = true)
        private Boolean aBoolField;

        @JsonProperty(required = true)
        private int intField;

        @JsonProperty(required = true)
        private Integer aIntField;

        // this field will be ignored
        @JsonIgnore private int ignoredField;

        // this field will not be required
        @JsonProperty(/*required = false*/ ) private long longField;

        @JsonProperty(required = true)
        private Long aLongField;

        @JsonProperty(required = true)
        private short shortField;

        @JsonProperty(required = true)
        private Short aShortField;

        @JsonProperty(required = true)
        private double doubleField;

        @JsonProperty(required = true)
        private Double aDoubleField;

        @JsonProperty(required = true)
        private float floatField;

        @JsonProperty(required = true)
        private Float aFloatField;

        @JsonProperty(required = true)
        private int[] interArrayField;

        @JsonProperty(required = true)
        private Integer[] aInterArrayField;
    }

    @Test
    public void test_generator_withCustomTypeWithOnlyBuiltIns() throws JsonSchemaGenerationException {
        JsonSchemaService.JsonSchemaGenerator generator = getJsonSchemaGenerator();

        JsonSchema jsonSchema = generator.generate(CustomType.class);
        assertThat(jsonSchema.getOriginalType()).isEqualTo(CustomType.class);

        Map<String, Object> actualProperties = new HashMap<>();
        for (JsonSchemaProperty property : jsonSchema.getProperties()) {
            actualProperties.put(property.key(), property.value());
        }

        //noinspection unchecked
        assertThat(actualProperties)
                .hasSize(4)
                .containsEntry("description", CUSTOM_TYPE_DESCRIPTION)
                .containsEntry("type", "object")
                .hasEntrySatisfying(
                        "properties",
                        properties ->
                                assertThat((Map<String, Object>) properties)
                                        .containsEntry("stringField", mapOfType("string"))
                                        .containsEntry("boolField", mapOfType("boolean"))
                                        .containsEntry("aBoolField", mapOfType("boolean"))
                                        .containsEntry("intField", mapOfType("integer"))
                                        .containsEntry("aIntField", mapOfType("integer"))
                                        .containsEntry("longField", mapOfType("integer"))
                                        .containsEntry("aLongField", mapOfType("integer"))
                                        .containsEntry("shortField", mapOfType("integer"))
                                        .containsEntry("aShortField", mapOfType("integer"))
                                        .containsEntry("doubleField", mapOfType("number"))
                                        .containsEntry("aDoubleField", mapOfType("number"))
                                        .containsEntry("floatField", mapOfType("number"))
                                        .containsEntry("aFloatField", mapOfType("number"))
                                        .containsEntry(
                                                "interArrayField",
                                                mapOfArrayType(mapOfType("integer")))
                                        .containsEntry(
                                                "aInterArrayField",
                                                mapOfArrayType(mapOfType("integer"))))
                .hasEntrySatisfying(
                        "required",
                        required ->
                                assertThat((List<String>) required)
                                        .containsExactlyInAnyOrder(
                                                "aBoolField",
                                                "aDoubleField",
                                                "aFloatField",
                                                "aInterArrayField",
                                                "aIntField",
                                                "aLongField",
                                                "aShortField",
                                                "intField",
                                                "doubleField",
                                                "floatField",
                                                "interArrayField",
                                                "shortField",
                                                "stringField"));
    }

    private Map<String, String> mapOfType(String type) {
        return singletonMap("type", type);
    }

    private Map<String, Object> mapOfArrayType(Object mapOfItemType) {
        return mapOf(entry("type", "array"), entry("items", mapOfItemType));
    }

    private static final String NESTED_CUSTOM_TYPE_DESCRIPTION = "NestedCustom type description";

    @SuppressWarnings("unused")
    @JsonClassDescription(NESTED_CUSTOM_TYPE_DESCRIPTION)
    static class NestedCustomType {
        @JsonProperty(required = true)
        private String stringField;

        // this field will not be required
        @JsonProperty(/*required = false */ ) private boolean boolField;

        @JsonProperty(required = true)
        private Boolean aBoolField;
    }

    @SuppressWarnings("unused")
    @JsonClassDescription(CUSTOM_TYPE_DESCRIPTION)
    static class OuterCustomType {
        @JsonProperty(required = true)
        private String stringField;

        // this field will not be required
        @JsonProperty(/*required = false */ ) private boolean boolField;

        @JsonProperty(required = true)
        private List<String> stringsField;

        @JsonProperty(required = true)
        private NestedCustomType nestedCustomType;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test_generator_withCustomTypeWithNestedCustomType() throws JsonSchemaGenerationException {
        JsonSchemaService.JsonSchemaGenerator generator = getJsonSchemaGenerator();

        JsonSchema jsonSchema = generator.generate(OuterCustomType.class);
        assertThat(jsonSchema.getOriginalType()).isEqualTo(OuterCustomType.class);

        Map<String, Object> actualProperties = new HashMap<>();
        for (JsonSchemaProperty property : jsonSchema.getProperties()) {
            actualProperties.put(property.key(), property.value());
        }

        Map<String, Object> expectedNestedCustomProperties =
                mapOf(
                        entry("description", NESTED_CUSTOM_TYPE_DESCRIPTION),
                        entry("type", "object"),
                        entry(
                                "properties",
                                mapOf(
                                        entry("stringField", mapOfType("string")),
                                        entry("boolField", mapOfType("boolean")),
                                        entry("aBoolField", mapOfType("boolean")))),
                        entry("required", Arrays.asList("aBoolField", "stringField")));

        assertThat(actualProperties)
                .hasSize(4)
                .containsEntry("description", CUSTOM_TYPE_DESCRIPTION)
                .containsEntry("type", "object")
                .hasEntrySatisfying(
                        "properties",
                        properties ->
                                assertThat((Map<String, Object>) properties)
                                        .containsEntry("stringField", mapOfType("string"))
                                        .containsEntry("boolField", mapOfType("boolean"))
                                        .containsEntry(
                                                "stringsField", mapOfArrayType(mapOfType("string")))
                                        .containsEntry(
                                                "nestedCustomType", expectedNestedCustomProperties))
                .hasEntrySatisfying(
                        "required",
                        required ->
                                assertThat((List<String>) required)
                                        .containsExactlyInAnyOrder(
                                                "stringField", "nestedCustomType", "stringsField"));
    }

    private static final String NESTED_GENERIC_CUSTOM_TYPE_DESCRIPTION =
            "Nested Generic Custom type description";

    @SuppressWarnings("unused")
    @JsonClassDescription(NESTED_GENERIC_CUSTOM_TYPE_DESCRIPTION)
    static class NestedGenericCustomType<T> {
        @JsonProperty(required = true)
        private String stringField;

        @JsonProperty(required = true)
        private List<T> itemsField;
    }

    @SuppressWarnings("unused")
    @JsonClassDescription(CUSTOM_TYPE_DESCRIPTION)
    static class OuterGenericCustomType<T> {
        @JsonProperty(required = true)
        private NestedGenericCustomType<T> nestedCustomType;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void test_generator_withGenericCustomTypeWithNestedCustomType() throws JsonSchemaGenerationException {
        JsonSchemaService.JsonSchemaGenerator generator = getJsonSchemaGenerator();

        JsonSchema jsonSchema =
                generator.generate(
                        new TypeReference<OuterGenericCustomType<String>>() {}.getType());
        assertThat(jsonSchema.getOriginalType())
                .isEqualTo(new TypeReference<OuterGenericCustomType<String>>() {}.getType());

        Map<String, Object> actualProperties = new HashMap<>();
        for (JsonSchemaProperty property : jsonSchema.getProperties()) {
            actualProperties.put(property.key(), property.value());
        }

        Map<String, Object> expectedNestedCustomProperties =
                mapOf(
                        entry("description", NESTED_GENERIC_CUSTOM_TYPE_DESCRIPTION),
                        entry("type", "object"),
                        entry(
                                "properties",
                                mapOf(
                                        entry("stringField", mapOfType("string")),
                                        entry(
                                                "itemsField",
                                                mapOf(
                                                        entry("type", "array"),
                                                        entry("items", mapOfType("string")))))),
                        entry("required", Arrays.asList("itemsField", "stringField")));

        assertThat(actualProperties)
                .hasSize(4)
                .containsEntry("description", CUSTOM_TYPE_DESCRIPTION)
                .containsEntry("type", "object")
                .hasEntrySatisfying(
                        "properties",
                        properties ->
                                assertThat((Map<String, Object>) properties)
                                        .containsEntry(
                                                "nestedCustomType", expectedNestedCustomProperties))
                .hasEntrySatisfying(
                        "required",
                        required ->
                                assertThat((List<String>) required)
                                        .containsExactlyInAnyOrder("nestedCustomType"));
    }

    private static final String BASE_TYPE_DESCRIPTION = "Base Custom type description";
    private static final String DERIVED_TYPE_DESCRIPTION = "Derived Custom type description";
    private static final String OTHER_DERIVED_TYPE_DESCRIPTION =
            "Other Derived Custom type description";
    private static final String DERIVED_NAME = "derived";
    private static final String OTHER_DERIVED_NAME = "other_derived";
    private static final String POLY_TYPE_FIELD = "type";

    @SuppressWarnings("unused")
    @JsonClassDescription(BASE_TYPE_DESCRIPTION)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = POLY_TYPE_FIELD)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = DerivedType.class, name = DERIVED_NAME),
        @JsonSubTypes.Type(value = OtherDerivedType.class, name = OTHER_DERIVED_NAME)
    })
    static class BaseType<T> {
        @JsonProperty(required = true)
        private String stringField;

        @JsonProperty(required = true)
        private List<T> itemsField;
    }

    @SuppressWarnings("unused")
    @JsonClassDescription(DERIVED_TYPE_DESCRIPTION)
    static class DerivedType<T> extends BaseType<T> {
        @JsonProperty(required = true)
        private String derivedFiled;
    }

    @SuppressWarnings("unused")
    @JsonClassDescription(OTHER_DERIVED_TYPE_DESCRIPTION)
    static class OtherDerivedType<T> extends BaseType<T> {
        @JsonProperty(required = true)
        private Integer otherDerivedField;
    }

    @SuppressWarnings("unused")
    @JsonClassDescription(CUSTOM_TYPE_DESCRIPTION)
    static class CustomTypeWithPolyType {
        @JsonProperty(required = true)
        private BaseType<String> polyField;
    }

    @Test
    public void test_generator_withCustomTypeWithPolyField() throws JsonSchemaGenerationException {
        JsonSchemaService.JsonSchemaGenerator generator = getJsonSchemaGenerator();

        JsonSchema jsonSchema = generator.generate(CustomTypeWithPolyType.class);
        assertThat(jsonSchema.getOriginalType()).isEqualTo(CustomTypeWithPolyType.class);

        Map<String, Object> actualProperties = new HashMap<>();
        for (JsonSchemaProperty property : jsonSchema.getProperties()) {
            actualProperties.put(property.key(), property.value());
        }

        Map.Entry<String, Object> expectedPolyTypeProperty =
                entry("type", singletonMap("const", "derived"));
        Map.Entry<String, Object> expectedBaseStringFieldProperty =
                entry("stringField", mapOfType("string"));
        Map.Entry<String, Object> expectedBaseItemsFieldProperty =
                entry(
                        "itemsField",
                        mapOf(entry("type", "array"), entry("items", mapOfType("string"))));

        Map<String, Object> expectedDerivedTypeProperties =
                mapOf(
                        entry("description", DERIVED_TYPE_DESCRIPTION),
                        entry("type", "object"),
                        entry(
                                "properties",
                                mapOf(
                                        entry("derivedFiled", mapOfType("string")),
                                        expectedBaseStringFieldProperty,
                                        expectedBaseItemsFieldProperty,
                                        entry("type", singletonMap("const", DERIVED_NAME)))),
                        entry(
                                "required",
                                Arrays.asList(
                                        "derivedFiled",
                                        "itemsField",
                                        "stringField",
                                        POLY_TYPE_FIELD)));

        Map<String, Object> expectedOtherDerivedTypeProperties =
                mapOf(
                        entry("description", OTHER_DERIVED_TYPE_DESCRIPTION),
                        entry("type", "object"),
                        entry(
                                "properties",
                                mapOf(
                                        entry("otherDerivedField", mapOfType("integer")),
                                        expectedBaseStringFieldProperty,
                                        expectedBaseItemsFieldProperty,
                                        expectedPolyTypeProperty,
                                        entry("type", singletonMap("const", OTHER_DERIVED_NAME)))),
                        entry(
                                "required",
                                Arrays.asList(
                                        "itemsField",
                                        "otherDerivedField",
                                        "stringField",
                                        POLY_TYPE_FIELD)));

        assertThat(actualProperties)
                .hasSize(4)
                .containsEntry("description", CUSTOM_TYPE_DESCRIPTION)
                .containsEntry("type", "object")
                .containsEntry(
                        "properties",
                        singletonMap(
                                "polyField",
                                singletonMap(
                                        "anyOf",
                                        Arrays.asList(
                                                expectedDerivedTypeProperties,
                                                expectedOtherDerivedTypeProperties))))
                .containsEntry("required", singletonList("polyField"));
    }

    private static JsonSchemaService.JsonSchemaGenerator getJsonSchemaGenerator() {
        return new VictoolsJsonSchemaGenerator(
                VictoolsJsonSchemaServiceFactory.getSchemaGeneratorConfig());
    }
}
