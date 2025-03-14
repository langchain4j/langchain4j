package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PromptTextContent.class, name = "text"),
    @JsonSubTypes.Type(value = EmbeddedResource.class, name = "resource"),
    @JsonSubTypes.Type(value = PromptImageContent.class, name = "image")
})
public sealed interface PromptContent permits PromptTextContent, EmbeddedResource, PromptImageContent {

    @JsonProperty("type")
    default String getType() {
        return type().toString().toLowerCase();
    }

    Type type();

    PromptTextContent asText();

    EmbeddedResource asResource();

    PromptImageContent asImage();

    enum Type {
        TEXT,
        RESOURCE,
        IMAGE
    }
}
