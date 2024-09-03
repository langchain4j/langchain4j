package dev.langchain4j.model.gemini;

public enum GeminiType {
    TYPE_UNSPECIFIED,
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    ARRAY,
    OBJECT;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
