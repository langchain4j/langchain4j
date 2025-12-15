package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.langchain4j.data.message.Content;
import java.util.Locale;

/**
 * A holder for one of ['McpTextContent', 'McpImageContent', 'McpEmbeddedResource'] objects from the MCP protocol schema.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = McpTextContent.class, name = "text"),
    @JsonSubTypes.Type(value = McpEmbeddedResource.class, name = "resource"),
    @JsonSubTypes.Type(value = McpImageContent.class, name = "image")
})
public sealed interface McpPromptContent permits McpTextContent, McpEmbeddedResource, McpImageContent {

    @JsonProperty("type")
    default String getType() {
        return type().toString().toLowerCase(Locale.ROOT);
    }

    Type type();

    Content toContent();

    enum Type {
        TEXT,
        RESOURCE,
        IMAGE
    }
}
