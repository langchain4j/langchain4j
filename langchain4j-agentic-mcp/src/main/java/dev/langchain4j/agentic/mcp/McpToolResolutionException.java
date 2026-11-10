package dev.langchain4j.agentic.mcp;

import dev.langchain4j.agentic.agent.AgentUnavailableException;
import dev.langchain4j.agentic.internal.LazyResolution;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Thrown when the MCP tool wrapped by an MCP client agent cannot be resolved, because the MCP client cannot be
 * created or the tools of the MCP server cannot be listed. The tool is resolved lazily, the first time it is needed,
 * which is normally the first invocation of the agent, so this exception reaches the caller of that invocation and
 * the error handler of the agentic system, where it can be recognized to skip an agent whose MCP server is
 * unavailable, by this type or by its supertype {@link AgentUnavailableException}, common to every integration. A
 * resolution that does not complete within the resolution timeout of the agent, when one is set, is interrupted and
 * reported with this exception too, with a {@link TimeoutException} as cause. As long as a resolution that timed
 * out has not reacted to its interruption, the tool cannot be resolved again: the invocations arriving in the
 * meantime fail at once, with a message saying that the previous resolution is still running. Concurrent first
 * invocations wait on the same resolution and all fail when it fails, each with its own exception sharing that
 * cause, but a failed resolution is not cached: the next invocation retries it. A retry from the error handler
 * happens at once, so it should be bounded by the handler itself. A tool that is not found on the server, or a
 * missing tool name when the server exposes several tools, is a configuration error instead, reported with an
 * {@code AgenticSystemConfigurationException}.
 *
 * @since 1.24.0
 */
public class McpToolResolutionException extends AgentUnavailableException {

    private final String toolName;

    public McpToolResolutionException(String agentName, String toolName, Throwable cause) {
        super(agentName, message(agentName, toolName), cause);
        this.toolName = toolName;
    }

    /**
     * Creates the exception reporting that the tool was not resolved within the resolution timeout, or, when the
     * cause says so, that the resolution which timed out before is still running.
     *
     * @since 1.24.0
     */
    public McpToolResolutionException(String agentName, String toolName, Duration timeout, TimeoutException cause) {
        super(
                agentName,
                message(agentName, toolName)
                        + (cause instanceof LazyResolution.StillRunningException
                                ? ": the previous resolution is still running after its timeout of "
                                : " within ")
                        + LazyResolution.describe(timeout),
                cause);
        this.toolName = toolName;
    }

    private static String message(String agentName, String toolName) {
        return "Failed to resolve the MCP tool " + (toolName != null ? "'" + toolName + "' " : "")
                + "of the MCP client agent '" + agentName + "'";
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
