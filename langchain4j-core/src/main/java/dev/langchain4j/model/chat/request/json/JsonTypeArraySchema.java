package dev.langchain4j.model.chat.request.json;

import dev.langchain4j.Experimental;
import java.util.Arrays;

@Experimental
public class JsonTypeArraySchema implements JsonSchemaElement {

    private final String[] types;

    public JsonTypeArraySchema(String[] types) {
        this.types = types;
    }

    public JsonTypeArraySchema(Builder builder) {
        this.types = builder.types;
    }

    public String[] getTypes() {
        return types;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String[] types;

        public Builder types(String[] types) {
            this.types = types;
            return this;
        }

        public JsonTypeArraySchema build() {
            return new JsonTypeArraySchema(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Arrays.equals(types, ((JsonTypeArraySchema) o).types);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(types);
    }

    @Override
    public String toString() {
        return "JsonTypeArraySchema {" + "types = " + Arrays.toString(types) + " }";
    }
}
