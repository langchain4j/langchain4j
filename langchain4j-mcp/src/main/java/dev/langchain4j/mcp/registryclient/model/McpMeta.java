package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.McpJsonConversions;
import java.util.LinkedHashMap;
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
    public Map<String, Object> publisherProvided() {
        if (publisherProvided == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        publisherProvided.forEach((k, v) -> result.put(k, McpJsonConversions.toValue(v)));
        return result;
    }

    /**
     * @deprecated use {@link #publisherProvided()}, which does not expose Jackson types.
     */
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
