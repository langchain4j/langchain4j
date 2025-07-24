package dev.langchain4j.model.chat.request.json;

import static dev.langchain4j.internal.Utils.quoted;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public class JsonNativeSchema implements JsonSchemaElement {

    private final String description;
    private final JsonNode schema;

    public JsonNativeSchema(Builder builder) {
        this.description = builder.description;
        this.schema = builder.schema;
    }

    @Override
    public String description() {
        return description;
    }

    public JsonNode getSchema() {
        return schema;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        public JsonNode schema;
        private String description;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder schema(JsonNode schema) {
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
