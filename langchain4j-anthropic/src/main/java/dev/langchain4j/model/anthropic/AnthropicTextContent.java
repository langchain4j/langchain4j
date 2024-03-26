package dev.langchain4j.model.anthropic;

class AnthropicTextContent {

    String type = "text";
    String text;

    AnthropicTextContent(String text) {
        this.text = text;
    }
}
