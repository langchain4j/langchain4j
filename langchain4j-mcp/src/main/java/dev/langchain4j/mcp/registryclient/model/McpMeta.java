package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class McpMeta {

    @JsonProperty("io.modelcontextprotocol.registry/official")
    private McpOfficialMeta official;

    // Held as plain values so that any JSON codec can read this type.
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

    @Override
    public String toString() {
        return "McpMeta{" +
                "official=" + official +
                ", publisherProvided=" + publisherProvided +
                '}';
    }
}
