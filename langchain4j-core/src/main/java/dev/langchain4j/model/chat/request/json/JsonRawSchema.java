package dev.langchain4j.model.chat.request.json;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.util.Objects;

public class JsonRawSchema implements JsonSchemaElement {

    private final String schema;

    public JsonRawSchema(Builder builder) {
        this.schema = ensureNotBlank(builder.schema, "schema");
    }

    @Override
    public String description() {
        return null;
    }

    public String schema() {
        return schema;
    }

    public static JsonRawSchema from(String schema) {
        return builder().schema(schema).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        public String schema;

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public JsonRawSchema build() {
            return new JsonRawSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JsonRawSchema that = (JsonRawSchema) o;
        return Objects.equals(this.schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema);
    }

    @Override
    public String toString() {
        return "JsonRawSchema {" + "schema = " + quoted(schema) + " }";
    }
}
