package dev.langchain4j.model.anthropic;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class AnthropicImageContentSource {

    String type;
    String mediaType;
    String data;
}