package dev.langchain4j.model.output.structured.json;

import dev.langchain4j.Experimental;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.model.output.structured.json.JsonType.STRING;

@Experimental
public class JsonStringSchema extends JsonSchemaElement {

    public static final JsonStringSchema STRING_SCHEMA = JsonStringSchema.builder().build();

    private final String description;

    public JsonStringSchema(Builder builder) {
        super(STRING);
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

        public JsonStringSchema build() {
            return new JsonStringSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonStringSchema that = (JsonStringSchema) o;
        return Objects.equals(this.description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description);
    }

    @Override
    public String toString() {
        return "JsonStringSchema {" +
                "description = " + quoted(description) +
                " }";
    }
}
