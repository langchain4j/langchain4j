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
    private final Map<String, JsonSchemaElement> definitions;

    public JsonObjectSchema(Builder builder) {
        this.description = builder.description;
        this.properties = copyIfNotNull(builder.properties);
        this.required = copyIfNotNull(builder.required);
        this.additionalProperties = builder.additionalProperties;
        this.definitions = copyIfNotNull(builder.definitions);
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

    /**
     * Used together with {@link JsonReferenceSchema} when recursion is required.
     */
    public Map<String, JsonSchemaElement> definitions() {
        return definitions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
        private List<String> required = new ArrayList<>();
        private Boolean additionalProperties;
        private Map<String, JsonSchemaElement> definitions;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addStringProperty(String)
         * @see #addStringProperty(String, Consumer)
         * @see #addIntegerProperty(String)
         * @see #addIntegerProperty(String, Consumer)
         * @see #addNumberProperty(String)
         * @see #addNumberProperty(String, Consumer)
         * @see #addBooleanProperty(String)
         * @see #addBooleanProperty(String, Consumer)
         * @see #addEnumProperty(String, Class)
         * @see #addEnumProperty(String, String...)
         * @see #addEnumProperty(String, Consumer)
         * @see #addArrayProperty(String, JsonSchemaElement)
         * @see #addArrayProperty(String, Consumer)
         * @see #addObjectProperty(String, Consumer)
         */
        public Builder properties(Map<String, JsonSchemaElement> properties) {
            this.properties = properties;
            return this;
        }

        /**
         * Adds a single property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addStringProperty(String)
         * @see #addStringProperty(String, Consumer)
         * @see #addIntegerProperty(String)
         * @see #addIntegerProperty(String, Consumer)
         * @see #addNumberProperty(String)
         * @see #addNumberProperty(String, Consumer)
         * @see #addBooleanProperty(String)
         * @see #addBooleanProperty(String, Consumer)
         * @see #addEnumProperty(String, Class)
         * @see #addEnumProperty(String, String...)
         * @see #addEnumProperty(String, Consumer)
         * @see #addArrayProperty(String, JsonSchemaElement)
         * @see #addArrayProperty(String, Consumer)
         * @see #addObjectProperty(String, Consumer)
         */
        public Builder addProperty(String name, JsonSchemaElement jsonSchemaElement) {
            this.properties.put(name, jsonSchemaElement);
            return this;
        }

        /**
         * Adds a single string property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addStringProperty(String name) {
            this.properties.put(name, new JsonStringSchema());
            return this;
        }

        /**
         * Adds a single string property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addStringProperty(String name, Consumer<JsonStringSchema.Builder> builderConsumer) {
            JsonStringSchema.Builder builder = JsonStringSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        /**
         * Adds a single integer property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addIntegerProperty(String name) {
            this.properties.put(name, new JsonIntegerSchema());
            return this;
        }

        /**
         * Adds a single integer property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addIntegerProperty(String name, Consumer<JsonIntegerSchema.Builder> builderConsumer) {
            JsonIntegerSchema.Builder builder = JsonIntegerSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        /**
         * Adds a single number property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addNumberProperty(String name) {
            this.properties.put(name, new JsonNumberSchema());
            return this;
        }

        /**
         * Adds a single number property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addNumberProperty(String name, Consumer<JsonNumberSchema.Builder> builderConsumer) {
            JsonNumberSchema.Builder builder = JsonNumberSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        /**
         * Adds a single boolean property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addBooleanProperty(String name) {
            this.properties.put(name, new JsonBooleanSchema());
            return this;
        }

        /**
         * Adds a single boolean property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addBooleanProperty(String name, Consumer<JsonBooleanSchema.Builder> builderConsumer) {
            JsonBooleanSchema.Builder builder = JsonBooleanSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        /**
         * Adds a single enum property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addEnumProperty(String name, Class<? extends Enum<?>> enumClass) {
            this.properties.put(name, JsonEnumSchema.builder().enumValues(enumClass).build());
            return this;
        }

        /**
         * Adds a single enum property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addEnumProperty(String name, String... enumValues) {
            this.properties.put(name, JsonEnumSchema.builder().enumValues(enumValues).build());
            return this;
        }

        /**
         * Adds a single enum property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addEnumProperty(String name, Consumer<JsonEnumSchema.Builder> builderConsumer) {
            JsonEnumSchema.Builder builder = JsonEnumSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        /**
         * Adds a single array property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addArrayProperty(String name, Consumer<JsonArraySchema.Builder> builderConsumer) {
            JsonArraySchema.Builder builder = JsonArraySchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

        /**
         * Adds a single array property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addArrayProperty(String name, JsonSchemaElement items) {
            this.properties.put(name, JsonArraySchema.builder().items(items).build());
            return this;
        }

        /**
         * Adds a single object property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #properties(Map)
         * @see #addProperty(String, JsonSchemaElement)
         */
        public Builder addObjectProperty(String name, Consumer<JsonObjectSchema.Builder> builderConsumer) {
            JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
            builderConsumer.accept(builder);
            this.properties.put(name, builder.build());
            return this;
        }

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
         * Used together with {@link JsonReferenceSchema} when recursion is required.
         */
        public Builder definitions(Map<String, JsonSchemaElement> definitions) {
            this.definitions = definitions;
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
                && Objects.equals(this.definitions, that.definitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, properties, required, additionalProperties, definitions);
    }

    @Override
    public String toString() {
        return "JsonObjectSchema {" +
                "description = " + quoted(description) +
                ", properties = " + properties +
                ", required = " + required +
                ", additionalProperties = " + additionalProperties +
                ", definitions = " + definitions +
                " }";
    }
}
