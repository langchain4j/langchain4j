package dev.langchain4j.model.chat.request.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

public class JsonEnumSchema implements JsonSchemaElement {

    private final String description;
    private final List<String> enumValues;

    public JsonEnumSchema(Builder builder) {
        this.description = builder.description;
        this.enumValues = copy(ensureNotEmpty(builder.enumValues, "enumValues"));
    }

    @Override
    public String description() {
        return description;
    }

    public List<String> enumValues() {
        return enumValues;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private List<String> enumValues;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder enumValues(List<String> enumValues) {
            this.enumValues = enumValues;
            return this;
        }

        public Builder enumValues(String... enumValues) {
            return enumValues(List.of(enumValues));
        }

        public JsonEnumSchema build() {
            return new JsonEnumSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonEnumSchema that = (JsonEnumSchema) o;
        return Objects.equals(this.description, that.description)
                && Objects.equals(this.enumValues, that.enumValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, enumValues);
    }

    @Override
    public String toString() {
        return "JsonEnumSchema {" +
                "description = " + quoted(description) +
                ", enumValues = " + enumValues +
                " }";
    }
}
