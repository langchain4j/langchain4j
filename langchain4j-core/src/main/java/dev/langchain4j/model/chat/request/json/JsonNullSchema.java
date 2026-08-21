package dev.langchain4j.model.chat.request.json;

public class JsonNullSchema implements JsonSchemaElement {

    @Override
    public String description() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return JsonNullSchema.class.hashCode();
    }

    @Override
    public String toString() {
        return "JsonNullSchema {}";
    }
}
