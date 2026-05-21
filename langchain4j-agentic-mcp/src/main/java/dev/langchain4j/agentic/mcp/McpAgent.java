package dev.langchain4j.agentic.mcp;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.McpClientBuilder;
import dev.langchain4j.mcp.client.McpClient;

/**
 * Provides type-safe factory methods to create MCP client agent builders.
 * <p>
 * An MCP client agent wraps a single MCP tool as a non-AI agent, allowing it to be composed
 * with other agents in sequences, loops, supervisors, and other workflow patterns.
 *
 * <pre>{@code
 * UntypedAgent agent = McpAgentBuilder
 *         .mcpBuilder(mcpClient)
 *         .toolName("my_tool")
 *         .inputKeys("arg1", "arg2")
 *         .outputKey("result")
 *         .build();
 * }</pre>
 */
public class McpAgent {

    private McpAgent() {}

    /**
     * Creates a builder for an untyped MCP client agent.
     *
     * @param mcpClient the MCP client instance used to discover and execute the tool
     * @return a new McpClientBuilder instance
     */
    public static McpClientBuilder<UntypedAgent> builder(McpClient mcpClient) {
        return builder(mcpClient, UntypedAgent.class);
    }

    /**
     * Creates a builder for a typed MCP client agent implementing the given agent service interface.
     *
     * @param mcpClient the MCP client instance used to discover and execute the tool
     * @param agentServiceClass the class of the agent service interface
     * @return a new McpClientBuilder instance
     */
    public static <T> McpClientBuilder<T> builder(McpClient mcpClient, Class<T> agentServiceClass) {
        return new DefaultMcpClientBuilder<>(mcpClient, agentServiceClass);
    }
}
