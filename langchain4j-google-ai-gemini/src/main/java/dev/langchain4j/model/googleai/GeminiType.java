package dev.langchain4j.model.googleai;

enum GeminiType {
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
