package dev.langchain4j.model.gemini;

public enum GeminiLanguage {
    PYTHON,
    LANGUAGE_UNSPECIFIED;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
