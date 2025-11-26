package dev.langchain4j.mcp.registryclient.model;

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
