package dev.langchain4j.model.googleai;

import lombok.Data;

@Data
class GeminiErrorContainer {
    private final GeminiError error;

    GeminiErrorContainer(GeminiError error) {
        this.error = error;
    }
}
