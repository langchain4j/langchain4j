package dev.langchain4j.model.anthropic;

class AnthropicTextContent {

    private final String type = "text";
    private final String text;

    AnthropicTextContent(String text) {
        this.text = text;
    }
}
