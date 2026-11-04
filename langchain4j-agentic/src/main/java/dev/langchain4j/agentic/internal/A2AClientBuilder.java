package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.A2AStreamingClientListener;
import dev.langchain4j.agentic.observability.AgentListener;
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
