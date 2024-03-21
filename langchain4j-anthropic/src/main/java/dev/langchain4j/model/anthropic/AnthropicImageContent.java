package dev.langchain4j.model.anthropic;

class AnthropicImageContent {

    String type = "image";
    AnthropicImageContentSource source;

    AnthropicImageContent(String mediaType, String data) {
        this.source = new AnthropicImageContentSource("base64", mediaType, data);
    }
}
