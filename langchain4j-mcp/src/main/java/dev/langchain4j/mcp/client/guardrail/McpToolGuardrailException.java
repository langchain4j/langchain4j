package dev.langchain4j.mcp.client.guardrail;

/**
 * Thrown by an MCP tool guardrail to reject a tool invocation.
 * The exception message is returned to the LLM as the tool error.
 */
public class McpToolGuardrailException extends RuntimeException {

    public McpToolGuardrailException(String message) {
        super(message);
    }

    public McpToolGuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
