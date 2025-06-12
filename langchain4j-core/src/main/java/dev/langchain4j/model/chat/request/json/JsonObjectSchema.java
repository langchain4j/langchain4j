package dev.langchain4j.model.chat.request.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Arrays.asList;

public class JsonObjectSchema implements JsonSchemaElement {

    private final String description;
    private final Map<String, JsonSchemaElement> properties;
    private final List<String> required;
    private final Boolean additionalProperties;
    private final Map<String, JsonSchemaElement> definitions;

    public JsonObjectSchema(Builder builder) {
        this.description = builder.description;
        this.properties = copy(builder.properties);
        this.required = copy(builder.required);
        this.additionalProperties = builder.additionalProperties;
        this.definitions = copy(builder.definitions);
    }

    @Override
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
        private final Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
        private List<String> required;
        private Boolean additionalProperties;
        private Map<String, JsonSchemaElement> definitions;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Adds all properties in the parameter Map to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addStringProperty(String)
         * @see #addStringProperty(String, String)
         * @see #addIntegerProperty(String)
         * @see #addIntegerProperty(String, String)
         * @see #addNumberProperty(String)
         * @see #addNumberProperty(String, String)
         * @see #addBooleanProperty(String)
         * @see #addBooleanProperty(String, String)
         * @see #addEnumProperty(String, List)
         * @see #addEnumProperty(String, List, String)
         */
        public Builder addProperties(Map<String, JsonSchemaElement> properties) {
            this.properties.putAll(properties);
            return this;
        }

        /**
         * Adds a single property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addProperties(Map)
         * @see #addStringProperty(String)
         * @see #addStringProperty(String, String)
         * @see #addIntegerProperty(String)
         * @see #addIntegerProperty(String, String)
         * @see #addNumberProperty(String)
         * @see #addNumberProperty(String, String)
         * @see #addBooleanProperty(String)
         * @see #addBooleanProperty(String, String)
         * @see #addEnumProperty(String, List)
         * @see #addEnumProperty(String, List, String)
         */
        public Builder addProperty(String name, JsonSchemaElement jsonSchemaElement) {
            this.properties.put(name, jsonSchemaElement);
            return this;
        }

        /**
         * Adds a single string property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addStringProperty(String, String)
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addProperties(Map)
         */
        public Builder addStringProperty(String name) {
            this.properties.put(name, new JsonStringSchema());
            return this;
        }

        /**
         * Adds a single string property with a description to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addStringProperty(String)
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addProperties(Map)
         */
        public Builder addStringProperty(String name, String description) {
            this.properties.put(name, JsonStringSchema.builder().description(description).build());
            return this;
        }

        /**
         * Adds a single integer property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addIntegerProperty(String, String)
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addProperties(Map)
         */
        public Builder addIntegerProperty(String name) {
            this.properties.put(name, new JsonIntegerSchema());
            return this;
        }

        /**
         * Adds a single integer property with a description to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addIntegerProperty(String)
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addProperties(Map)
         */
        public Builder addIntegerProperty(String name, String description) {
            this.properties.put(name, JsonIntegerSchema.builder().description(description).build());
            return this;
        }

        /**
         * Adds a single number property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addNumberProperty(String, String)
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addProperties(Map)
         */
        public Builder addNumberProperty(String name) {
            this.properties.put(name, new JsonNumberSchema());
            return this;
        }

        /**
         * Adds a single number property with a description to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addNumberProperty(String)
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addProperties(Map)
         */
        public Builder addNumberProperty(String name, String description) {
            this.properties.put(name, JsonNumberSchema.builder().description(description).build());
            return this;
        }

        /**
         * Adds a single boolean property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addBooleanProperty(String, String)
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addProperties(Map)
         */
        public Builder addBooleanProperty(String name) {
            this.properties.put(name, new JsonBooleanSchema());
            return this;
        }

        /**
         * Adds a single boolean property with a description to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addBooleanProperty(String)
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addProperties(Map)
         */
        public Builder addBooleanProperty(String name, String description) {
            this.properties.put(name, JsonBooleanSchema.builder().description(description).build());
            return this;
        }

        /**
         * Adds a single enum property to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addEnumProperty(String, List, String)
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addProperties(Map)
         */
        public Builder addEnumProperty(String name, List<String> enumValues) {
            this.properties.put(name, JsonEnumSchema.builder().enumValues(enumValues).build());
            return this;
        }

        /**
         * Adds a single enum property with a description to the properties of this JSON object.
         * Please note that {@link #required(List)} should be set explicitly if you want the properties to be mandatory.
         *
         * @see #addEnumProperty(String, List)
         * @see #addProperty(String, JsonSchemaElement)
         * @see #addProperties(Map)
         */
        public Builder addEnumProperty(String name, List<String> enumValues, String description) {
            this.properties.put(name, JsonEnumSchema.builder().enumValues(enumValues).description(description).build());
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
