package dev.langchain4j.model.chat.request.json;

import dev.langchain4j.Experimental;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

@Experimental
public class JsonRefSchema implements JsonSchemaElement { // TODO name: JsonSchemaReference?

    private final String ref; // TODO name

    public JsonRefSchema(Builder builder) {
        this.ref = builder.ref;
    }

    public String ref() {
        return ref;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String ref;

        public Builder ref(String ref) {
            this.ref = ref;
            return this;
        }

        public JsonRefSchema build() {
            return new JsonRefSchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonRefSchema that = (JsonRefSchema) o;
        return Objects.equals(this.ref, that.ref);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ref);
    }

    @Override
    public String toString() {
        return "JsonRefSchema {" +
                "ref = " + quoted(ref) +
                " }";
    }

    // TODO
    public static JsonRefSchema withRef(String ref) { // TODO name
        return builder().ref(ref).build();
    }
}
