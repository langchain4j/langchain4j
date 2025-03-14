package dev.langchain4j.mcp.client;

public class McpException extends RuntimeException {

    private final McpError mcpError;

    public McpException(McpError mcpError) {
        super(mcpError.toString());
        this.mcpError = mcpError;
    }

    public McpError getMcpError() {
        return mcpError;
    }
}
