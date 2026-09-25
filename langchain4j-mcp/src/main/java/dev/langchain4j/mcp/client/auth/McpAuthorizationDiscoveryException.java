package dev.langchain4j.mcp.client.auth;

import dev.langchain4j.Experimental;

/**
 * Thrown when the authorization server of an MCP server cannot be discovered, or when the
 * discovered metadata fails the validation the MCP authorization specification requires.
 */
@Experimental
public class McpAuthorizationDiscoveryException extends RuntimeException {

    public McpAuthorizationDiscoveryException(String message) {
        super(message);
    }

    public McpAuthorizationDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
