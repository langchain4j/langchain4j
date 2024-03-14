package dev.langchain4j.model.anthropic;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class AnthropicImageContentSource {

    private final String type;
    private final String mediaType;
    private final String data;
}