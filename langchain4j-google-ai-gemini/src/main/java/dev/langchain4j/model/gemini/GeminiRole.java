package dev.langchain4j.model.gemini;

public enum GeminiRole {
    USER,
    MODEL;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
