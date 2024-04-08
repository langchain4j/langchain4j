package dev.langchain4j.model.anthropic;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class AnthropicImageContentSource {

    public String type;
    public String mediaType;
    public String data;
}