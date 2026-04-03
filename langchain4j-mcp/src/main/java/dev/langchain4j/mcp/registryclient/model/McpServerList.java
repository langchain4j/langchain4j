package dev.langchain4j.mcp.registryclient.model;

import java.util.List;

public class McpServerList {

    private List<McpGetServerResponse> servers;
    private McpMetadata metadata;

    public List<McpGetServerResponse> getServers() {
        return servers;
    }

    public McpMetadata getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "McpServerList{" +
                "servers=" + servers +
                ", metadata=" + metadata +
                '}';
    }
}
