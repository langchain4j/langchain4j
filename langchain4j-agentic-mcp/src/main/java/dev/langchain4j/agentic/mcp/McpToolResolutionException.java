package dev.langchain4j.agentic.mcp;

import dev.langchain4j.agentic.agent.AgentUnavailableException;

/**
 * Thrown when the MCP tool wrapped by an MCP client agent cannot be resolved, because the MCP client cannot be
 * created or the tools of the MCP server cannot be listed. The tool is resolved lazily, the first time it is needed,
 * which is normally the first invocation of the agent, so this exception reaches the caller of that invocation and
 * the error handler of the agentic system, where it can be recognized to skip an agent whose MCP server is
 * unavailable, by this type or by its supertype {@link AgentUnavailableException}, common to every integration. A
 * failed resolution is not cached: the next invocation retries it. A retry from the error handler happens at once,
 * so it should be bounded by the handler itself. A tool that is not found on the server, or a missing tool name when
 * the server exposes several tools, is a configuration error instead, reported with an
 * {@code AgenticSystemConfigurationException}.
 *
 * @since 1.24.0
 */
public class McpToolResolutionException extends AgentUnavailableException {

    private final String toolName;

    public McpToolResolutionException(String agentName, String toolName, Throwable cause) {
        super(
                agentName,
                "Failed to resolve the MCP tool " + (toolName != null ? "'" + toolName + "' " : "")
                        + "of the MCP client agent '" + agentName + "'",
                cause);
        this.toolName = toolName;
    }

    /**
     * @return the name of the tool configured for the agent, or {@code null} when the agent binds the single tool
     * of its MCP server
     * @since 1.24.0
     */
    public String toolName() {
        return toolName;
    }
}
