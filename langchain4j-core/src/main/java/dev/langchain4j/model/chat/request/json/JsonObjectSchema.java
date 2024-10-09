package dev.langchain4j.model.chat.request.json;

import dev.langchain4j.Experimental;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Arrays.asList;

@Experimental
public class JsonObjectSchema implements JsonSchemaElement {

    private final String description;
    private final Map<String, JsonSchemaElement> properties;
    private final List<String> required;
    private final Boolean additionalProperties;
    private final Map<String, JsonSchemaElement> defs; // TODO name

    public JsonObjectSchema(Builder builder) {
        this.description = builder.description;
        this.properties = copyIfNotNull(builder.properties);
        this.required = copyIfNotNull(builder.required);
        this.additionalProperties = builder.additionalProperties;
        this.defs = copyIfNotNull(builder.defs);
    }

    public String description() {
        return description;
    }

    public Map<String, JsonSchemaElement> properties() {
        return properties;
    }

    public List<String> required() {
        return required;
    }

    public Boolean additionalProperties() {
        return additionalProperties;
    }

    public Map<String, JsonSchemaElement> defs() { // TODO name
        return defs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
        private List<String> required = new ArrayList<>();
        private Boolean additionalProperties;
        private Map<String, JsonSchemaElement> defs;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder properties(Map<String, JsonSchemaElement> properties) {
            this.properties = properties;
            return this;
        }

        /**
         * TODO helper method
         *
         * @param name
         * @param jsonSchemaElement
         * @return
         */
        public Builder addProperty(String name, JsonSchemaElement jsonSchemaElement) {
            this.properties.put(name, jsonSchemaElement);
            return this;
        }

        // TODO other types

        public Builder addStringProperty(String name) {
            this.properties.put(name, JsonStringSchema.builder().build());
            return this;
        }

        public Builder addStringProperty(String name, Consumer<JsonStringSchema.Builder> builderConsumer) {
            JsonStringSchema.Builder builder = JsonStringSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        public Builder addIntegerProperty(String name) {
            this.properties.put(name, JsonIntegerSchema.builder().build());
            return this;
        }

        public Builder addIntegerProperty(String name, Consumer<JsonIntegerSchema.Builder> builderConsumer) {
            JsonIntegerSchema.Builder builder = JsonIntegerSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        public Builder addNumberProperty(String name) {
            this.properties.put(name, JsonNumberSchema.builder().build());
            return this;
        }

        public Builder addNumberProperty(String name, Consumer<JsonNumberSchema.Builder> builderConsumer) {
            JsonNumberSchema.Builder builder = JsonNumberSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        public Builder addBooleanProperty(String name) {
            this.properties.put(name, JsonBooleanSchema.builder().build());
            return this;
        }

        public Builder addBooleanProperty(String name, Consumer<JsonBooleanSchema.Builder> builderConsumer) {
            JsonBooleanSchema.Builder builder = JsonBooleanSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        public Builder addEnumProperty(String name, String... enumValues) {
            this.properties.put(name, JsonEnumSchema.builder().enumValues(enumValues).build());
            return this;
        }

        public Builder addEnumProperty(String name, Consumer<JsonEnumSchema.Builder> builderConsumer) {
            JsonEnumSchema.Builder builder = JsonEnumSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        public Builder addArrayProperty(String name, Consumer<JsonArraySchema.Builder> builderConsumer) {
            JsonArraySchema.Builder builder = JsonArraySchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        public Builder addObjectProperty(String name, Consumer<JsonObjectSchema.Builder> builderConsumer) {
            JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        // TODO addOptionalProperty? addProperty(..., boolean required)?

        public Builder required(List<String> required) {
            this.required = required;
            return this;
        }

        public Builder required(String... required) {
            return required(asList(required));
        }

        public Builder additionalProperties(Boolean additionalProperties) {
            this.additionalProperties = additionalProperties;
            return this;
        }

        /**
         * TODO
         *
         * @param defs
         * @return
         */
        // TODO name
        public Builder defs(Map<String, JsonSchemaElement> defs) {
            this.defs = defs;
            return this;
        }

        public JsonObjectSchema build() {
            return new JsonObjectSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonObjectSchema that = (JsonObjectSchema) o;
        return Objects.equals(this.description, that.description)
                && Objects.equals(this.properties, that.properties)
                && Objects.equals(this.required, that.required)
                && Objects.equals(this.additionalProperties, that.additionalProperties)
                && Objects.equals(this.defs, that.defs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, properties, required, additionalProperties, defs);
    }

    @Override
    public String toString() {
        return "JsonObjectSchema {" +
                "description = " + quoted(description) +
                ", properties = " + properties +
                ", required = " + required +
                ", additionalProperties = " + additionalProperties +
                ", defs = " + defs + // TODO name
                " }";
    }
}
