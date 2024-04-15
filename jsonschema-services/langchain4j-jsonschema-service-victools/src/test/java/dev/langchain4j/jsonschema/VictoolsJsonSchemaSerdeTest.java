package dev.langchain4j.jsonschema;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.exception.JsonSchemaSerializationException;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class VictoolsJsonSchemaSerdeTest implements WithAssertions {

    @SuppressWarnings("unused")
    static class CustomType {

        @JsonProperty(required = true)
        private String stringField;

        @JsonProperty(required = true)
        private boolean boolField;

        @JsonProperty(required = true)
        private int intField;

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            CustomType other = (CustomType) obj;
            return stringField.equals(other.stringField)
                    && boolField == other.boolField
                    && intField == other.intField;
        }

    }

    @Test
    public void test_serde() throws JsonSchemaSerializationException, JsonSchemaParsingException {
        CustomType pojo = new CustomType();
        pojo.stringField = ("hello");
        pojo.boolField = (true);
        pojo.intField = (42);

        JacksonJsonSchemaSerde serde = new JacksonJsonSchemaSerde();
        String serialized = serde.serialize(pojo);
        assertThat(serialized)
                .isEqualTo("{\"stringField\":\"hello\",\"boolField\":true,\"intField\":42}");

        JsonNode serializedJsonNode = serde.parse(serialized);
        assertThatCode(
                () -> assertThat(serde.deserialize(serializedJsonNode, CustomType.class))
                        .isEqualTo(pojo))
                .doesNotThrowAnyException();
    }

    private static final String CUSTOM_TYPE_DESCRIPTION = "Custom type description";
    private static final String DERIVED_TYPE_DESCRIPTION = "Derived Custom type description";
    private static final String OTHER_DERIVED_TYPE_DESCRIPTION =
            "Other Derived Custom type description";
    private static final String DERIVED_NAME = "derived";
    private static final String OTHER_DERIVED_NAME = "other_derived";
    private static final String POLY_TYPE_FIELD = "type";

    @SuppressWarnings("unused")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = POLY_TYPE_FIELD)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = DerivedType.class, name = DERIVED_NAME),
            @JsonSubTypes.Type(value = OtherDerivedType.class, name = OTHER_DERIVED_NAME)
    })
    static class BaseType<T> {

        @JsonProperty(required = true)
        String stringField;

        @JsonProperty(required = true)
        List<T> itemsField;

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            BaseType<?> other = (BaseType<?>) obj;
            return stringField.equals(other.stringField) && itemsField.equals(other.itemsField);
        }

    }

    @SuppressWarnings("unused")
    @JsonClassDescription(DERIVED_TYPE_DESCRIPTION)
    static class DerivedType<T> extends BaseType<T> {

        @JsonProperty(required = true)
        Double derivedFiled;

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            DerivedType<?> other = (DerivedType<?>) obj;
            return super.equals(obj) && derivedFiled.equals(other.derivedFiled);
        }

    }

    @SuppressWarnings("unused")
    @JsonClassDescription(OTHER_DERIVED_TYPE_DESCRIPTION)
    static class OtherDerivedType<T> extends BaseType<T> {

        @JsonProperty(required = true)
        Integer otherDerivedField;

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            OtherDerivedType<?> other = (OtherDerivedType<?>) obj;
            return super.equals(obj) && otherDerivedField.equals(other.otherDerivedField);
        }

    }

    @SuppressWarnings("unused")
    @JsonClassDescription(CUSTOM_TYPE_DESCRIPTION)
    static class CustomTypeWithPolyType {

        @JsonProperty(required = true)
        BaseType<String> polyField;

        @JsonProperty(required = true)
        String stringField;

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            CustomTypeWithPolyType other = (CustomTypeWithPolyType) obj;
            return polyField.equals(other.polyField) && stringField.equals(other.stringField);
        }

    }

    @Test
    public void test_serde_withPolymorphicType() throws JsonSchemaSerializationException {
        CustomTypeWithPolyType pojo = new CustomTypeWithPolyType();
        DerivedType<String> derived = new DerivedType<>();

        derived.stringField = "derived";
        derived.itemsField = Arrays.asList("item1", "item2");
        derived.derivedFiled = 42.0;
        pojo.polyField = derived;
        pojo.stringField = "stringField";

        JacksonJsonSchemaSerde serde = new JacksonJsonSchemaSerde();
        String serialized = serde.serialize(pojo);
        assertThat(serialized)
                .isEqualTo(
                        "{\"polyField\":{"
                                + "\"type\":\"derived\","
                                + "\"stringField\":\"derived\","
                                + "\"itemsField\":[\"item1\",\"item2\"],"
                                + "\"derivedFiled\":42.0},"
                                + "\"stringField\":\"stringField\"}");

        assertThatCode(
                () -> assertThat(
                        serde.deserialize(
                                serde.parse(serialized),
                                CustomTypeWithPolyType.class))
                        .isEqualTo(pojo))
                .doesNotThrowAnyException();
    }
}
