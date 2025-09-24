package dev.langchain4j.mcp.registryclient.model;

import java.util.List;

public class McpServerList {

    private List<McpServer> servers;
    private McpMetadata metadata;

    public List<McpServer> getServers() {
        return servers;
    }

    public McpMetadata getMetadata() {
        return metadata;
    }
}
