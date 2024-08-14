package dev.langchain4j.model.chat.request.json;

import com.google.gson.annotations.SerializedName;
import dev.langchain4j.Experimental;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

@Experimental
public class JsonObjectSchema implements JsonSchemaElement {

    private final String description;
    private final Map<String, JsonSchemaElement> properties;
    private final List<String> required;
    @SerializedName("additionalProperties")
    private final Boolean additionalProperties;

    public JsonObjectSchema(Builder builder) {
        this.description = builder.description;
        this.properties = new LinkedHashMap<>(ensureNotEmpty(builder.properties, "properties"));
        this.required = copyIfNotNull(builder.required);
        this.additionalProperties = builder.additionalProperties;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private Map<String, JsonSchemaElement> properties = new LinkedHashMap<>();
        private List<String> required = new ArrayList<>();
        private Boolean additionalProperties;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder properties(Map<String, JsonSchemaElement> properties) {
            this.properties = properties;
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
                && Objects.equals(this.additionalProperties, that.additionalProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, properties, required, additionalProperties);
    }

    @Override
    public String toString() {
        return "JsonObjectSchema {" +
                "description = " + quoted(description) +
                ", properties = " + properties +
                ", required = " + required +
                ", additionalProperties = " + additionalProperties +
                " }";
    }
}
