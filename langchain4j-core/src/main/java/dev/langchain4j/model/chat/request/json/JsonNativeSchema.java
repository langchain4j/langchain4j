package dev.langchain4j.model.chat.request.json;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.util.Objects;

public class JsonNativeSchema implements JsonSchemaElement {

    private final String schema;
    
    public JsonNativeSchema(Builder builder) {
        this.schema = ensureNotBlank(builder.schema, "schema");
    }

    @Override
    public String description() {
        return null;
    }

    public String schema() {
        return schema;
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

        public JsonNativeSchema build() {
            return new JsonNativeSchema(this);
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
        JsonNativeSchema that = (JsonNativeSchema) o;
        return Objects.equals(this.schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema);
    }

    @Override
    public String toString() {
        return "JsonNativeSchema {" + "schema = " + quoted(schema) + " }";
    }
}
