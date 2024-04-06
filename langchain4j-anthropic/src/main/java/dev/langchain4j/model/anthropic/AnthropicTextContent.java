package dev.langchain4j.model.anthropic;

public class AnthropicTextContent {

    public String type = "text";
    public String text;

    public AnthropicTextContent(String text) {
        this.text = text;
    }
}
