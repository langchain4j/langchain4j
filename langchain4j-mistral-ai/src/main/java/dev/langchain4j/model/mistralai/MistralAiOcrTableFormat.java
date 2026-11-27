package dev.langchain4j.model.mistralai;

/**
 * How tables detected in a document are rendered inside the markdown returned by OCR.
 */
public enum MistralAiOcrTableFormat {
    MARKDOWN("markdown"),
    HTML("html");

    private final String value;

    MistralAiOcrTableFormat(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
