package dev.langchain4j.agentic.agent;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Thrown when an agent cannot be used because something it depends on is unavailable, typically the server of a
 * remote agent, like an A2A or an MCP one, from which the agent card or the tool is fetched lazily, the first time
 * it is needed: normally the first invocation of the agent, or the first request of its description when none is
 * configured. It is the common type of these transient failures, whatever the integration they come from, as
 * opposed to a configuration error like a tool that does not exist on its server, which is reported with its own
 * exception. The supervisor leaves a subagent whose description fails with this exception out of the current
 * invocation, with a warning, the HTML reports show its description as unavailable, and an error handler can
 * recognize it in the cause chain of the {@link AgentInvocationException} it receives to skip the agent. The
 * failure is not cached: the next invocation tries again.
 *
 * @since 1.24.0
 */
public class AgentUnavailableException extends LangChain4jException {

    private final String agentName;

    public AgentUnavailableException(String agentName, String message, Throwable cause) {
        super(message, cause);
        this.agentName = agentName;
    }

    /**
     * @return the name of the unavailable agent
     * @since 1.24.0
     */
    public String agentName() {
        return agentName;
    }
}
