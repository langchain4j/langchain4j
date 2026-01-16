package dev.langchain4j.model.chat.request.json;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

public class JsonBooleanSchema implements JsonSchemaElement {

    private final String description;

    public JsonBooleanSchema() {
        this.description = null;
    }

    public JsonBooleanSchema(Builder builder) {
        this.description = builder.description;
    }

    @Override
    public String description() {
        return description;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public JsonBooleanSchema build() {
            return new JsonBooleanSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonBooleanSchema that = (JsonBooleanSchema) o;
        return Objects.equals(this.description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description);
    }

    @Override
    public String toString() {
        return "JsonBooleanSchema {" +
                "description = " + quoted(description) +
                " }";
    }
}
