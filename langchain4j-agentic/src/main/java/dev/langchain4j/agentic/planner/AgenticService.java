package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Fluent builder interface for configuring and assembling an agentic service
 * composed of one or more sub-agents orchestrated by a planner.
 *
 * @param <T> the self-referencing builder type (for fluent chaining)
 * @param <A> the type of the built agent proxy
 */
public interface AgenticService<T, A> {

    /**
     * Builds the agentic service, returning a proxy that can be invoked to
     * execute the configured workflow.
     *
     * @return the built agent proxy
     */
    A build();

    /**
     * Sets the sub-agents that participate in this agentic workflow.
     *
     * @param agents the sub-agents to orchestrate
     * @return this builder for fluent chaining
     */
    T subAgents(Object... agents);

    /**
     * Sets the sub-agents that participate in this agentic workflow.
     *
     * @param agents a collection of sub-agents to orchestrate
     * @return this builder for fluent chaining
     */
    T subAgents(Collection<?> agents);

    /**
     * Registers a callback invoked before each agent call, allowing inspection
     * or modification of the {@link AgenticScope} (e.g. to inject variables).
     *
     * @param beforeCall the callback to invoke before each call
     * @return this builder for fluent chaining
     */
    T beforeCall(Consumer<AgenticScope> beforeCall);

    /**
     * Sets the name of this agentic service. If not provided, the method name
     * of the agent interface is used.
     *
     * @param name the name of the agentic service
     * @return this builder for fluent chaining
     */
    T name(String name);

    /**
     * Sets the description of this agentic service. The description should be
     * clear enough for a language model to understand the agent's purpose.
     *
     * @param description the description of the agentic service
     * @return this builder for fluent chaining
     */
    T description(String description);

    /**
     * Sets the key under which the final output of this workflow is stored
     * in the {@link AgenticScope}.
     *
     * @param outputKey the name of the output variable
     * @return this builder for fluent chaining
     */
    T outputKey(String outputKey);

    /**
     * Sets a strongly typed key for the output variable, enforcing type safety
     * when retrieving the result from the {@link AgenticScope}. Use this as an
     * alternative to {@link #outputKey(String)}.
     *
     * @param outputKey the class representing the typed output variable
     * @return this builder for fluent chaining
     */
    T outputKey(Class<? extends TypedKey<?>> outputKey);

    /**
     * Sets a custom function to extract the final output from the
     * {@link AgenticScope} at the end of the workflow. Use this when the
     * output requires transformation or aggregation beyond a simple key lookup.
     *
     * @param output a function that receives the scope and returns the output
     * @return this builder for fluent chaining
     */
    T output(Function<AgenticScope, Object> output);

    /**
     * Registers an error handler that is invoked when a sub-agent fails. The
     * handler receives an {@link ErrorContext} and returns an
     * {@link ErrorRecoveryResult} indicating whether to propagate the exception,
     * retry the failed agent, or return a fallback result.
     *
     * @param errorHandler the error handling function
     * @return this builder for fluent chaining
     */
    T errorHandler(Function<ErrorContext, ErrorRecoveryResult> errorHandler);

    /**
     * Enables or disables cross-agent compensation. When enabled and any agent
     * in the hierarchy fails, all previously successful tool invocations that
     * have {@code @CompensateFor} actions are compensated in reverse
     * chronological order, making the workflow's side effects atomic.
     * Defaults to {@code false}.
     *
     * @param compensateOnError whether to enable cross-agent compensation
     * @return this builder for fluent chaining
     */
    T compensateOnError(boolean compensateOnError);

    /**
     * Registers an {@link AgentListener} to observe agent invocations, tool
     * executions, and lifecycle events within this agentic service.
     *
     * @param listeners the listener to register
     * @return this builder for fluent chaining
     */
    T listener(AgentListener listeners);
}
