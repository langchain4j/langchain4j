package dev.langchain4j.mcp.registryclient.model;

import dev.langchain4j.mcp.client.McpJsonConversions;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public class McpMeta {

    @JsonProperty("io.modelcontextprotocol.registry/official")
    private McpOfficialMeta official;

    @JsonProperty("io.modelcontextprotocol.registry/publisher-provided")
    private Map<String, JsonNode> publisherProvided;

    public McpOfficialMeta getOfficial() {
        return official;
    }

    /**
     * Returns publisher-provided metadata as plain values, so consumers do not need a JSON library.
     */
    public Map<String, Object> getPublisherProvidedValues() {
        if (publisherProvided == null) {
            return null;
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        publisherProvided.forEach((k, v) -> result.put(k, McpJsonConversions.toValue(v)));
        return result;
    }

    @Deprecated(since = "1.20.0", forRemoval = true)
    public Map<String, JsonNode> getPublisherProvided() {
        return publisherProvided;
    }

    @Override
    public String toString() {
        return "McpMeta{" +
                "official=" + official +
                ", publisherProvided=" + publisherProvided +
                '}';
    }
}
