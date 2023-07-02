package dev.langchain4j.model.huggingface;

class TextGenerationResponse {

    private final String generatedText;

    TextGenerationResponse(String generatedText) {
        this.generatedText = generatedText;
    }

    public String generatedText() {
        return generatedText;
    }
}
