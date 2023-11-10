package dev.langchain4j.model.huggingface.client;

public class TextGenerationResponse {

    private final String generatedText;

    public TextGenerationResponse(String generatedText) {
        this.generatedText = generatedText;
    }

    public String generatedText() {
        return generatedText;
    }
}
