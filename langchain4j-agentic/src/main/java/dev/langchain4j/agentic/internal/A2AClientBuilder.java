package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.A2AStreamingClientListener;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Builder for creating A2A client proxies that communicate with remote agents
 * over the Agent-to-Agent (A2A) protocol.
 *
 * @param <T> the agent service interface type
 */
public interface A2AClientBuilder<T> {

    /**
     * Sets the name of the agent, from which its id in the reports, in the agents list sent to a supervisor and in
     * the error handler context is derived. When not set, a typed agent takes the name of the annotation of its agent
     * method or, when that is not set either, the name of the method, and an untyped one gets a placeholder derived
     * from the host, port and path of the A2A server URL, so that the name is known without fetching the agent
     * card, which happens only on the first invocation. The name never changes once the agent is built, even after
     * the card has been fetched.
     *
     * @param name the name of the agent, ignored when null or blank
     * @return this builder for method chaining
     */
    default A2AClientBuilder<T> name(String name) {
        return this;
    }

    /**
     * Sets the description of the agent, used by the supervisor and the other planners to decide when to invoke
     * it. When neither this method nor the annotation of the agent method set it, the description is read from the
     * agent card, which is fetched on the first call to {@link AgentInstance#description()}: for a subagent of a
     * supervisor this happens on the first invocation of the supervisor, which then depends on the availability of
     * the remote agent, so an explicit description is recommended there.
     *
     * @param description the description of the agent, ignored when null or blank
     * @return this builder for method chaining
     */
    default A2AClientBuilder<T> description(String description) {
        return this;
    }

    /**
     * Sets the state keys whose values will be sent as message parts to the remote agent.
     *
     * @param inputKeys the names of the input keys
     * @return this builder for method chaining
     */
    A2AClientBuilder<T> inputKeys(String... inputKeys);

    /**
     * Sets the state key under which the agent's response will be stored.
     *
     * @param outputKey the name of the output key
     * @return this builder for method chaining
     */
    A2AClientBuilder<T> outputKey(String outputKey);

    /**
     * Sets whether the agent should be invoked asynchronously, allowing the
     * workflow to continue without waiting for the agent's result.
     * The result is awaited only where it is read: by another agent taking it as an argument, or by the output
     * function or output key of the agentic system.
     * <p>
     * When nothing reads the result, the call to the agentic system returns while the agent is still running, and
     * the result or the failure of the agent is then reported only to its {@link AgentListener}. The next call on
     * the same memory id waits for these agents to complete before it starts.
     *
     * @param async {@code true} for asynchronous invocation
     * @return this builder for method chaining
     */
    A2AClientBuilder<T> async(boolean async);

    /**
     * Sets an {@link AgentListener} for observing agent invocations.
     *
     * @param agentListener the listener to attach
     * @return this builder for method chaining
     */
    A2AClientBuilder<T> listener(AgentListener agentListener);

    /**
     * Sets the condition under which the agent runs when it is invoked as a subagent of an agentic system. When the
     * condition is false the remote agent is not called and the agent is skipped. It can be evaluated more than once,
     * so it must be free of side effects.
     *
     * @param activationCondition the predicate deciding whether the agent runs
     * @return this builder for method chaining
     */
    default A2AClientBuilder<T> activationCondition(Predicate<AgenticScope> activationCondition) {
        return activationCondition("<unknown>", activationCondition);
    }

    /**
     * Sets the condition under which the agent runs when it is invoked as a subagent of an agentic system. When the
     * condition is false the remote agent is not called and the agent is skipped. It can be evaluated more than once,
     * so it must be free of side effects.
     *
     * @param description a human-readable description of the condition, used in logs, listeners and reports
     * @param activationCondition the predicate deciding whether the agent runs
     * @return this builder for method chaining
     */
    A2AClientBuilder<T> activationCondition(String description, Predicate<AgenticScope> activationCondition);

    /**
     * Sets a customizer that will be applied to the underlying a2a-java SDK
     * {@code ClientBuilder} before the {@code Client} is constructed.
     * <p>
     * The wildcard type avoids a compile-time dependency on the a2a-java SDK
     * in this module. At runtime the value must be a
     * {@code Consumer<org.a2aproject.sdk.client.ClientBuilder>}.
     *
     * @param clientCustomizer a {@code Consumer<ClientBuilder>} from the a2a-java SDK
     * @return this builder for method chaining
     */
    A2AClientBuilder<T> clientCustomizer(Consumer<?> clientCustomizer);

    /**
     * Sets an {@link A2AStreamingClientListener} for observing updates received
     * from the remote agent during streaming invocation.
     *
     * <p>Like {@code DefaultA2AClientBuilder}, which uses {@code a2a-java}, can be used
     * with the following example:
     * <pre>{@code
     * streamingClientListener((TaskUpdateEvent event) -> {
     *     // handle the streaming event
     * });
     * }</pre>
     *
     * <p>The listener is invoked with task update events received from the remote agent.
     *
     * @param clientListener the listener to attach
     * @return this builder for method chaining
     */
    A2AClientBuilder<T> streamingClientListener(A2AStreamingClientListener<?> clientListener);

    /**
     * Sets the tenant identifier that will be sent with every A2A message.
     *
     * @param tenant the tenant identifier
     * @return this builder for method chaining
     */
    default A2AClientBuilder<T> tenant(String tenant) {
        return this;
    }

    /**
     * Builds the A2A client proxy implementing the agent service interface.
     *
     * @return the proxy instance
     */
    T build();
}
