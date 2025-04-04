package dev.langchain4j.model.chat.request.json;

import dev.langchain4j.Experimental;

@Experimental
public class JsonNullSchema implements JsonSchemaElement {

    public JsonNullSchema() {}

    public JsonNullSchema(Builder builder) {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        public JsonNullSchema build() {
            return new JsonNullSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public String toString() {
        return "JsonNullSchema{}";
    }
}
