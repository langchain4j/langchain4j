package dev.langchain4j.model.chat.request.json;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

/**
 * Can reference {@link JsonObjectSchema} when recursion is required.
 * When used, the {@link JsonObjectSchema#definitions()} of the root JSON schema element
 * should contain an entry with a key equal to the {@link #reference()} of this {@link JsonReferenceSchema}.
 */
public class JsonReferenceSchema implements JsonSchemaElement {

    private final String reference;

    public JsonReferenceSchema(Builder builder) {
        this.reference = builder.reference;
    }

    public String reference() {
        return reference;
    }

    @Override
    public String description() {
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String reference;

        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public JsonReferenceSchema build() {
            return new JsonReferenceSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonReferenceSchema that = (JsonReferenceSchema) o;
        return Objects.equals(this.reference, that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference);
    }

    @Override
    public String toString() {
        return "JsonReferenceSchema {" +
                "reference = " + quoted(reference) +
                " }";
    }
}
