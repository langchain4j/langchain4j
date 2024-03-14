package dev.langchain4j.model.anthropic;

class AnthropicImageContent {

    private final String type = "image";
    private final AnthropicImageContentSource source;

    AnthropicImageContent(String mediaType, String data) {
        this.source = new AnthropicImageContentSource("base64", mediaType, data);
    }
}
