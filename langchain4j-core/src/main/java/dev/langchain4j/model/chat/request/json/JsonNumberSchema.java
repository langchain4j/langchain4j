package dev.langchain4j.model.chat.request.json;

import dev.langchain4j.Experimental;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

@Experimental
public class JsonNumberSchema implements JsonSchemaElement {

    public static final JsonNumberSchema JSON_NUMBER_SCHEMA = JsonNumberSchema.builder().build();

    private final String description;

    public JsonNumberSchema(Builder builder) {
        this.description = builder.description;
    }

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

        public JsonNumberSchema build() {
            return new JsonNumberSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonNumberSchema that = (JsonNumberSchema) o;
        return Objects.equals(this.description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description);
    }

    @Override
    public String toString() {
        return "JsonNumberSchema {" +
                "description = " + quoted(description) +
                " }";
    }
}
