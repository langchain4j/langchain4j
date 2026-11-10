package dev.langchain4j.agentic.mcp;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.McpClientBuilder;
import dev.langchain4j.mcp.client.McpClient;
import java.util.function.Supplier;

/**
 * Provides type-safe factory methods to create MCP client agent builders.
 * <p>
 * An MCP client agent wraps a single MCP tool as a non-AI agent, allowing it to be composed
 * with other agents in sequences, loops, supervisors, and other workflow patterns. The tool is looked up on the MCP
 * server the first time it is needed, which is normally the first invocation of the agent, never when the agent is
 * built, and the {@link McpClient} itself is created at that moment too when it is given through a supplier.
 *
 * <pre>{@code
 * UntypedAgent agent = McpAgent
 *         .builder(mcpClient)
 *         .toolName("my_tool")
 *         .inputKeys("arg1", "arg2")
 *         .outputKey("result")
 *         .build();
 * }</pre>
 */
public class McpAgent {

    private McpAgent() {}

    /**
     * Creates a builder for an untyped MCP client agent. The given client is created by the caller, so a
     * {@code DefaultMcpClient} has already connected to its server: to defer that too, use {@link #builder(Supplier)}.
     *
     * @param mcpClient the MCP client instance used to discover and execute the tool
     * @return a new McpClientBuilder instance
     */
    public static McpClientBuilder<UntypedAgent> builder(McpClient mcpClient) {
        return builder(mcpClient, UntypedAgent.class);
    }

    /**
     * Creates a builder for a typed MCP client agent implementing the given agent service interface. The given
     * client is created by the caller, so a {@code DefaultMcpClient} has already connected to its server: to defer
     * that too, use {@link #builder(Supplier, Class)}.
     *
     * @param mcpClient the MCP client instance used to discover and execute the tool
     * @param agentServiceClass the class of the agent service interface
     * @return a new McpClientBuilder instance
     */
    public static <T> McpClientBuilder<T> builder(McpClient mcpClient, Class<T> agentServiceClass) {
        ensureNotNull(mcpClient, "mcpClient");
        Supplier<McpClient> mcpClientSupplier = () -> mcpClient;
        return builder(mcpClientSupplier, agentServiceClass);
    }

    /**
     * Creates a builder for an untyped MCP client agent whose MCP client is created lazily.
     * <p>
     * The supplier is invoked on a thread of the LangChain4j executor, not on the one of the invocation that first
     * needs the client, within the {@link McpClientBuilder#resolutionTimeout(java.time.Duration) resolution timeout}
     * of the agent when one is set, and the client it returns is then used by
     * every invocation of the agent, so it must not depend on per-request or per-user state, like the credentials of
     * the current user. When it fails, the invocations waiting for the client fail, it is invoked again the next time
     * the client is needed, and a
     * {@code DefaultMcpClient} that fails to connect stops its transport, so the supplier can reuse one or create it
     * inside. The agent never closes the client: to close it when it is no longer needed, keep a reference to it from
     * the supplier.
     *
     * @param mcpClientSupplier the supplier of the MCP client used to discover and execute the tool, invoked when the
     *                          client is first needed, which is normally the first invocation of the agent, so that
     *                          creating the client, which normally connects to the MCP server, does not require the
     *                          server to be up when the agent is built
     * @return a new McpClientBuilder instance
     * @since 1.24.0
     */
    public static McpClientBuilder<UntypedAgent> builder(Supplier<McpClient> mcpClientSupplier) {
        return builder(mcpClientSupplier, UntypedAgent.class);
    }

    /**
     * Creates a builder for a typed MCP client agent implementing the given agent service interface, whose MCP
     * client is created lazily.
     * <p>
     * The supplier is invoked on a thread of the LangChain4j executor, not on the one of the invocation that first
     * needs the client, within the {@link McpClientBuilder#resolutionTimeout(java.time.Duration) resolution timeout}
     * of the agent when one is set, and the client it returns is then used by
     * every invocation of the agent, so it must not depend on per-request or per-user state, like the credentials of
     * the current user. When it fails, the invocations waiting for the client fail, it is invoked again the next time
     * the client is needed, and a
     * {@code DefaultMcpClient} that fails to connect stops its transport, so the supplier can reuse one or create it
     * inside. The agent never closes the client: to close it when it is no longer needed, keep a reference to it from
     * the supplier.
     *
     * @param mcpClientSupplier the supplier of the MCP client used to discover and execute the tool, invoked when the
     *                          client is first needed, which is normally the first invocation of the agent, so that
     *                          creating the client, which normally connects to the MCP server, does not require the
     *                          server to be up when the agent is built
     * @param agentServiceClass the class of the agent service interface
     * @return a new McpClientBuilder instance
     * @since 1.24.0
     */
    public static <T> McpClientBuilder<T> builder(Supplier<McpClient> mcpClientSupplier, Class<T> agentServiceClass) {
        return new DefaultMcpClientBuilder<>(ensureNotNull(mcpClientSupplier, "mcpClientSupplier"), agentServiceClass);
    }
}
