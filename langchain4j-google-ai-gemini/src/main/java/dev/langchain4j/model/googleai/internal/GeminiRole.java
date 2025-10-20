package dev.langchain4j.model.googleai.internal;

import dev.langchain4j.Internal;

@Internal
public enum GeminiRole {
    USER,
    MODEL;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
