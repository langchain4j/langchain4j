package dev.langchain4j.model.output.structured.json;

import dev.langchain4j.Experimental;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.output.structured.json.JsonType.ARRAY;

@Experimental
public class JsonArraySchema extends JsonSchemaElement {

    private final String description;
    private final JsonSchemaElement items;

    public JsonArraySchema(Builder builder) {
        super(ARRAY);
        this.description = builder.description;
        this.items = ensureNotNull(builder.items, "items");
    }

    public String description() {
        return description;
    }

    public JsonSchemaElement items() {
        return items;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private JsonSchemaElement items;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder items(JsonSchemaElement items) {
            this.items = items;
            return this;
        }

        public JsonArraySchema build() {
            return new JsonArraySchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonArraySchema that = (JsonArraySchema) o;
        return Objects.equals(this.description, that.description)
                && Objects.equals(this.items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, items);
    }

    @Override
    public String toString() {
        return "JsonArraySchema {" +
                "description = " + quoted(description) +
                ", items = " + items +
                " }";
    }
}
