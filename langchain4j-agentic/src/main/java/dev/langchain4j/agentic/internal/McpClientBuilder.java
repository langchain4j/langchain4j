package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public interface McpClientBuilder<T> {

    /**
     * Sets the name of the MCP tool bound by the agent. When not set, the agent binds the single tool of the MCP
     * server, and fails at its first invocation when the server exposes more than one. The tool is looked up on the
     * server the first time it is needed, not when the agent is built.
     */
    McpClientBuilder<T> toolName(String toolName);

    /**
     * Sets the name of the agent, from which its id in the reports, in the agents list sent to a supervisor and in
     * the error handler context is derived. When not set, a typed agent takes the name of the annotation of its agent
     * method or, when that is not set either, the name of the method, and an untyped one takes its configured tool
     * name or, without one, the placeholder {@code mcp-agent}, so that the name is known without contacting the MCP
     * server, which happens only on the first invocation. The placeholder logs a warning when the agent is built, and
     * an agentic system can contain at most one agent named with it, otherwise its construction fails with an
     * {@code AgenticSystemConfigurationException}, since those agents could not be told apart. The name never changes
     * once the agent is built, even after the tool has been resolved.
     *
     * @param name the name of the agent, ignored when null or blank
     * @return this builder for method chaining
     */
    McpClientBuilder<T> name(String name);

    /**
     * Sets the description of the agent, used by the supervisor and the other planners to decide when to invoke
     * it. When neither this method nor the annotation of the agent method set it, the description is the one of the
     * MCP tool, which is resolved on the first call to {@link AgentInstance#description()}: for a subagent of a
     * supervisor this happens on the first invocation of the supervisor, which then depends on the availability of
     * the MCP server, so an explicit description is recommended there.
     *
     * @param description the description of the agent, ignored when null or blank
     * @return this builder for method chaining
     */
    McpClientBuilder<T> description(String description);

    /**
     * Sets the names of the input parameters of the MCP tool, read from the agentic scope when the agent is invoked.
     * They are mandatory for an untyped agent, which could not report any argument without them until its tool is
     * resolved, so {@link #build()} fails with an {@code IllegalArgumentException} when they are not set, while a
     * typed agent takes them from the parameters of its agent method when they are not set.
     *
     * @param inputKeys the names of the input keys
     * @return this builder for method chaining
     */
    McpClientBuilder<T> inputKeys(String... inputKeys);

    McpClientBuilder<T> outputKey(String outputKey);

    /**
     * Sets whether the agent should be invoked asynchronously, allowing the workflow to continue without waiting for
     * the agent's result.
     * The result is awaited only where it is read: by another agent taking it as an argument, or by the output
     * function or output key of the agentic system.
     * <p>
     * When nothing reads the result, the call to the agentic system returns while the agent is still running, and
     * the result or the failure of the agent is then reported only to its {@link AgentListener}. The next call on
     * the same memory id waits for these agents to complete before it starts.
     *
     * @param async {@code true} for asynchronous invocation
     * @return this builder
     */
    McpClientBuilder<T> async(boolean async);

    McpClientBuilder<T> listener(AgentListener agentListener);

    /**
     * Sets the condition under which the agent runs when it is invoked as a subagent of an agentic system. When the
     * condition is false the MCP tool is not called and the agent is skipped. It can be evaluated more than once, so
     * it must be free of side effects.
     */
    default McpClientBuilder<T> activationCondition(Predicate<AgenticScope> activationCondition) {
        return activationCondition("<unknown>", activationCondition);
    }

    /**
     * Sets the condition, with its description, under which the agent runs when it is invoked as a subagent of an
     * agentic system. When the condition is false the MCP tool is not called and the agent is skipped. It can be
     * evaluated more than once, so it must be free of side effects.
     */
    McpClientBuilder<T> activationCondition(String description, Predicate<AgenticScope> activationCondition);

    /**
     * Sets the time given to the resolution of the MCP tool, that is the creation of the MCP client by its supplier
     * and the listing of the tools of the MCP server, which happens the first time the tool is needed, normally on
     * the first invocation of the agent, on a thread of the LangChain4j executor. It does not limit the execution of
     * the tool. When the tool is not resolved within this time the resolution is interrupted and the invocation
     * fails with an {@code McpToolResolutionException} caused by a {@link TimeoutException}. Concurrent first
     * invocations wait on the same resolution, so none of them waits longer than this time, and they all fail when
     * it fails. An invocation arriving before the interrupted resolution has ended fails at once with the same
     * timeout, so the supplier should respond to the interruption, and the next one after that resolves the tool
     * again.
     * <p>
     * By default the resolution has no such limit and takes as long as the MCP client allows: a
     * {@code DefaultMcpClient} has its own timeouts, which still apply to each of its steps when this one is set, so
     * that the resolution ends at the first of them that expires.
     *
     * @param resolutionTimeout the time given to the resolution of the MCP tool, which must be positive, or
     *                          {@code null} for no such limit
     * @return this builder for method chaining
     * @throws IllegalArgumentException if the time is zero or negative
     * @since 1.24.0
     */
    McpClientBuilder<T> resolutionTimeout(Duration resolutionTimeout);

    T build();
}
