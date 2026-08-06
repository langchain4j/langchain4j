package dev.langchain4j.mcp.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.protocol.McpImplementation;
import java.util.List;

/**
 * Corresponds to the {@code DiscoverResult} type from the MCP schema.
 */
public class McpDiscoverResult {

    private List<String> supportedVersions;
    private JsonNode capabilities;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private McpImplementation serverInfo;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String instructions;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String resultType;

    public McpDiscoverResult() {}

    public List<String> getSupportedVersions() {
        return supportedVersions;
    }

    public void setSupportedVersions(List<String> supportedVersions) {
        this.supportedVersions = supportedVersions;
    }

    public JsonNode getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(JsonNode capabilities) {
        this.capabilities = capabilities;
    }

    public McpImplementation getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(McpImplementation serverInfo) {
        this.serverInfo = serverInfo;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }
}
