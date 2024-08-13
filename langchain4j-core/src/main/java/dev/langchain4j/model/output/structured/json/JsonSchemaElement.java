package dev.langchain4j.model.output.structured.json;

import dev.langchain4j.Experimental;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public abstract class JsonSchemaElement {
    // TODO will be used later by tools as well. right place?

    private final JsonType type;

    protected JsonSchemaElement(JsonType type) {
        this.type = ensureNotNull(type, "type");
    }

    public JsonType type() {
        return type;
    }
}
