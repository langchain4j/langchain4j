package dev.langchain4j.model.googleai;

enum GeminiRole {
    USER,
    MODEL;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
