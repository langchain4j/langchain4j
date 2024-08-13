package dev.langchain4j.model.output.structured.json;

import dev.langchain4j.Experimental;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.model.output.structured.json.JsonType.BOOLEAN;

@Experimental
public class JsonBooleanSchema extends JsonSchemaElement {

    public static final JsonBooleanSchema BOOLEAN_SCHEMA = JsonBooleanSchema.builder().build();

    private final String description;

    public JsonBooleanSchema(Builder builder) {
        super(BOOLEAN);
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
