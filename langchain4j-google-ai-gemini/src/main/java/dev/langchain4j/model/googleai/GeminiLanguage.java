package dev.langchain4j.model.googleai;

public enum GeminiLanguage {
    PYTHON,
    LANGUAGE_UNSPECIFIED;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
