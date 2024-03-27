package dev.langchain4j.model.anthropic;

public class AnthropicImageContent {

    public String type = "image";
    public AnthropicImageContentSource source;

    public AnthropicImageContent(String mediaType, String data) {
        this.source = new AnthropicImageContentSource("base64", mediaType, data);
    }
}
