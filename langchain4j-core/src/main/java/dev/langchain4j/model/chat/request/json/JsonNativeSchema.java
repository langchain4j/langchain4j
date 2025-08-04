package dev.langchain4j.model.chat.request.json;

import static dev.langchain4j.internal.Utils.quoted;

import java.util.Objects;

public class JsonNativeSchema implements JsonSchemaElement {

    private final String description;
    private final String schema;

    public JsonNativeSchema(Builder builder) {
        this.description = builder.description;
        this.schema = builder.schema;
    }

    @Override
    public String description() {
        return description;
    }

    public String schema() {
        return schema;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        public String schema;
        private String description;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

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
        return Objects.equals(this.description, that.description) && Objects.equals(this.schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, schema);
    }

    @Override
    public String toString() {
        return "JsonNativeSchema {" + "description = " + quoted(description) + ", schema = " + schema + " }";
    }
}
