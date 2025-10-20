package dev.langchain4j.model.googleai.internal;

enum GeminiLanguage {
    PYTHON,
    LANGUAGE_UNSPECIFIED;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
