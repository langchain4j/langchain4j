package dev.langchain4j.model.chat.request.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;

public class JsonAnyOfSchema implements JsonSchemaElement {

    private final String description;
    private final List<JsonSchemaElement> anyOf;

    public JsonAnyOfSchema(Builder builder) {
        this.description = builder.description;
        this.anyOf = copy(ensureNotEmpty(builder.anyOf, "anyOf"));
    }

    @Override
    public String description() {
        return description;
    }

    public List<JsonSchemaElement> anyOf() {
        return anyOf;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String description;
        private List<JsonSchemaElement> anyOf;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder anyOf(List<JsonSchemaElement> anyOf) {
            this.anyOf = anyOf;
            return this;
        }

        public Builder anyOf(JsonSchemaElement... anyOf) {
            return anyOf(asList(anyOf));
        }

        public JsonAnyOfSchema build() {
            return new JsonAnyOfSchema(this);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final JsonAnyOfSchema that)) return false;
        return Objects.equals(description, that.description)
                && Objects.equals(anyOf, that.anyOf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, anyOf);
    }

    @Override
    public String toString() {
        return "JsonAnyOfSchema {" +
                "description = " + quoted(description) +
                ", anyOf = " + anyOf +
                " }";
    }
}
