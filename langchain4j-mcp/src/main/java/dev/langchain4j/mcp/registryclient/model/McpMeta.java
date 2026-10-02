package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.transport.McpJson;
import java.util.LinkedHashMap;
import java.util.Map;

public class McpMeta {

    @JsonProperty("io.modelcontextprotocol.registry/official")
    private McpOfficialMeta official;

    // Held as plain values so that any JSON codec can read this type; the deprecated
    // getPublisherProvided() converts back to JsonNode for as long as it exists.
    @JsonProperty("io.modelcontextprotocol.registry/publisher-provided")
    private Map<String, Object> publisherProvided;

    public McpOfficialMeta getOfficial() {
        return official;
    }

    /**
     * Returns publisher-provided metadata as plain values, so consumers do not need a JSON library.
     */
    public Map<String, Object> publisherProvided() {
        return publisherProvided;
    }

    /**
     * @deprecated use {@link #publisherProvided()}, which does not expose Jackson types.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public Map<String, JsonNode> getPublisherProvided() {
        if (publisherProvided == null) {
            return null;
        }
        Map<String, JsonNode> result = new LinkedHashMap<>();
        publisherProvided.forEach((k, v) -> result.put(k, McpJson.parse(McpJson.serialize(v))));
        return result;
    }

    @Override
    public String toString() {
        return "McpMeta{" +
                "official=" + official +
                ", publisherProvided=" + publisherProvided +
                '}';
    }
}
