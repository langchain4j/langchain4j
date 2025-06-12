package dev.langchain4j.model.chat.request.json;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

public class JsonIntegerSchema implements JsonSchemaElement {

    private final String description;

    public JsonIntegerSchema() {
        this.description = null;
    }

    public JsonIntegerSchema(Builder builder) {
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

        public JsonIntegerSchema build() {
            return new JsonIntegerSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonIntegerSchema that = (JsonIntegerSchema) o;
        return Objects.equals(this.description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description);
    }

    @Override
    public String toString() {
        return "JsonIntegerSchema {" +
                "description = " + quoted(description) +
                " }";
    }
}
