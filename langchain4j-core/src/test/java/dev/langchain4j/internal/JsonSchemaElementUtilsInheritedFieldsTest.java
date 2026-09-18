package dev.langchain4j.internal;

import static dev.langchain4j.internal.JsonSchemaElementUtils.jsonSchemaElementFrom;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.output.structured.Description;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonSchemaElementUtilsInheritedFieldsTest {

    static class Parent {
        @Description("parent id")
        int id;

        @Description("parent name")
        String name;
    }

    static class Child extends Parent {
        String extra;
    }

    static class GrandChild extends Child {
        @Description("overridden name")
        String name;
    }

    static class ChildWithStaticShadow extends Parent {
        @Description("static name")
        static String name;

        String extra;
    }

    static class ParentWithIgnoredField {
        @JsonIgnore
        String ignored;

        String visible;
    }

    static class ChildWithIgnoredField extends ParentWithIgnoredField {
        String extra;
    }

    static class TypeWithIgnoredField {
        @JsonIgnore
        String ignored;

        String visible;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes(@JsonSubTypes.Type(value = PolymorphicChild.class, name = "child"))
    abstract static class PolymorphicParent {
        String inherited;
    }

    static class PolymorphicChild extends PolymorphicParent {
        String own;
    }

    @Test
    void should_not_include_inherited_fields_by_default() {
        JsonSchemaElement schema = jsonSchemaElementFrom(Child.class, false);

        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema obj = (JsonObjectSchema) schema;
        assertThat(obj.properties()).containsKey("extra");
        assertThat(obj.properties()).doesNotContainKey("id");
        assertThat(obj.properties()).doesNotContainKey("name");
    }

    @Test
    void should_include_inherited_fields_when_enabled() {
        JsonSchemaElement schema = jsonSchemaElementFrom(Child.class, true);

        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema obj = (JsonObjectSchema) schema;
        assertThat(obj.properties()).containsKey("id");
        assertThat(obj.properties()).containsKey("name");
        assertThat(obj.properties()).containsKey("extra");
    }

    @Test
    void child_field_should_shadow_parent_field() {
        JsonSchemaElement schema = jsonSchemaElementFrom(GrandChild.class, true);

        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema obj = (JsonObjectSchema) schema;
        assertThat(obj.properties()).containsKey("name");
        JsonSchemaElement nameSchema = obj.properties().get("name");
        assertThat(nameSchema).isInstanceOf(JsonStringSchema.class);
        assertThat(((JsonStringSchema) nameSchema).description()).isEqualTo("overridden name");
    }

    @Test
    void static_child_field_should_not_shadow_parent_field() {
        JsonSchemaElement schema = jsonSchemaElementFrom(ChildWithStaticShadow.class, true);

        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema obj = (JsonObjectSchema) schema;
        assertThat(obj.properties()).containsKeys("id", "name", "extra");
        JsonSchemaElement nameSchema = obj.properties().get("name");
        assertThat(nameSchema).isInstanceOf(JsonStringSchema.class);
        assertThat(((JsonStringSchema) nameSchema).description()).isEqualTo("parent name");
    }

    @Test
    void should_preserve_parent_first_ordering() {
        JsonSchemaElement schema = jsonSchemaElementFrom(Child.class, true);

        JsonObjectSchema obj = (JsonObjectSchema) schema;
        List<String> keys = new ArrayList<>(obj.properties().keySet());
        assertThat(keys).containsExactly("id", "name", "extra");
    }

    @Test
    void should_not_respect_json_ignore_when_only_inherited_fields_are_enabled() {
        JsonSchemaElement schema = jsonSchemaElementFrom(ChildWithIgnoredField.class, true, false);

        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema obj = (JsonObjectSchema) schema;
        assertThat(obj.properties()).containsKey("visible");
        assertThat(obj.properties()).containsKey("ignored");
        assertThat(obj.properties()).containsKey("extra");
    }

    @Test
    void should_respect_json_ignore_on_inherited_fields_when_enabled() {
        JsonSchemaElement schema = jsonSchemaElementFrom(ChildWithIgnoredField.class, true, true);

        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema obj = (JsonObjectSchema) schema;
        assertThat(obj.properties()).containsKey("visible");
        assertThat(obj.properties()).containsKey("extra");
        assertThat(obj.properties()).doesNotContainKey("ignored");
    }

    @Test
    void should_not_respect_json_ignore_on_declared_fields_by_default() {
        JsonSchemaElement schema = jsonSchemaElementFrom(TypeWithIgnoredField.class, false);

        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema obj = (JsonObjectSchema) schema;
        assertThat(obj.properties()).containsKey("visible");
        assertThat(obj.properties()).containsKey("ignored");
    }

    @Test
    void should_respect_json_ignore_on_declared_fields() {
        JsonSchemaElement schema = jsonSchemaElementFrom(TypeWithIgnoredField.class, false, true);

        assertThat(schema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema obj = (JsonObjectSchema) schema;
        assertThat(obj.properties()).containsKey("visible");
        assertThat(obj.properties()).doesNotContainKey("ignored");
    }

    @Test
    void should_include_inherited_fields_in_polymorphic_subtypes_when_enabled() {
        JsonSchemaElement schema = jsonSchemaElementFrom(PolymorphicParent.class, true);

        assertThat(schema).isInstanceOf(JsonAnyOfSchema.class);
        JsonAnyOfSchema anyOf = (JsonAnyOfSchema) schema;
        assertThat(anyOf.anyOf()).hasSize(1);

        JsonObjectSchema subtype = (JsonObjectSchema) anyOf.anyOf().get(0);
        assertThat(subtype.properties()).containsKey("kind");
        assertThat(subtype.properties()).containsKey("inherited");
        assertThat(subtype.properties()).containsKey("own");
    }
}
