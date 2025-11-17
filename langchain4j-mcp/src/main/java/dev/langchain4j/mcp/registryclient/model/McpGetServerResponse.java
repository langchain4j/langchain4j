package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class McpGetServerResponse {

    @JsonProperty("_meta")
    private McpMeta meta;

    private McpServer server;

    public McpMeta getMeta() {
        return meta;
    }

    public McpServer getServer() {
        return server;
    }

    @Override
    public String toString() {
        return "McpGetServerResponse{" +
                "meta=" + meta +
                ", server=" + server +
                '}';
    }
}
