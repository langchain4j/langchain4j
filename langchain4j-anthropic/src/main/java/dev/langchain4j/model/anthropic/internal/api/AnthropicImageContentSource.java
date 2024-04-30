package dev.langchain4j.model.anthropic.internal.api;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class AnthropicImageContentSource {

    public String type;
    public String mediaType;
    public String data;
}