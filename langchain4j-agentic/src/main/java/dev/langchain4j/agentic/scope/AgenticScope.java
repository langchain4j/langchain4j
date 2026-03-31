package dev.langchain4j.agentic.scope;

import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.invocation.LangChain4jManaged;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The AgenticScope class represents a common environment where agents belonging to the same
 * agentic system can share their state.
 * It maintains the state of the computation, tracks agent invocations, and provides
 * methods to allow agents to interact with the shared state.
 * <p>
 * Agents can register their calls, and the context of interactions is stored for later retrieval.
 * The class also provides methods to read and write state, manage agent invocations, and retrieve
 * the context as a conversation.
 */
public interface AgenticScope extends LangChain4jManaged {

    /**
     * Returns the unique memory identifier for this scope. This ID is used to associate the scope
     * with a specific conversation or session, and to look up persisted scopes from a store.
     *
     * @return the memory identifier
     */
    Object memoryId();

    /**
     * Writes a value into the shared state under the given key.
     * If the value is {@code null}, the key is removed from the state.
     *
     * @param key   the state key
     * @param value the value to store, or {@code null} to remove the key
     */
    void writeState(String key, Object value);

    /**
     * Writes a value into the shared state using a strongly typed key.
     * The key's name is derived from the {@link TypedKey} class.
     *
     * @param key   the typed key class
     * @param value the value to store
     * @param <T>   the type of the value
     */
    <T> void writeState(Class<? extends TypedKey<T>> key, T value);

    /**
     * Writes multiple key-value pairs into the shared state at once.
     *
     * @param newState a map of key-value pairs to store
     */
    void writeStates(Map<String, Object> newState);

    /**
     * Checks whether the shared state contains a non-blank value for the given key.
     *
     * @param key the state key
     * @return {@code true} if the key exists and its value is non-null (and non-blank for strings)
     */
    boolean hasState(String key);

    /**
     * Checks whether the shared state contains a non-blank value for the given typed key.
     *
     * @param key the typed key class
     * @return {@code true} if the key exists and its value is non-null (and non-blank for strings)
     */
    boolean hasState(Class<? extends TypedKey<?>> key);

    /**
     * Reads the value associated with the given key from the shared state.
     * If the value is a {@link dev.langchain4j.agentic.internal.DelayedResponse}, this method
     * blocks until the response is available.
     *
     * @param key the state key
     * @return the value, or {@code null} if the key is not present
     */
    Object readState(String key);

    /**
     * Reads the value associated with the given key from the shared state,
     * returning a default value if the key is not present.
     * If the value is a {@link dev.langchain4j.agentic.internal.DelayedResponse}, this method
     * blocks until the response is available.
     *
     * @param key          the state key
     * @param defaultValue the value to return if the key is not present
     * @param <T>          the type of the value
     * @return the value, or {@code defaultValue} if the key is not present
     */
    <T> T readState(String key, T defaultValue);

    /**
     * Reads the value associated with the given typed key from the shared state.
     * The key's name and default value are derived from the {@link TypedKey} class.
     * If the value is a {@link dev.langchain4j.agentic.internal.DelayedResponse}, this method
     * blocks until the response is available.
     *
     * @param key the typed key class
     * @param <T> the type of the value
     * @return the value, or the key's default value if not present
     */
    <T> T readState(Class<? extends TypedKey<T>> key);

    /**
     * Returns a live view of the entire shared state map.
     * Modifications to this map are reflected in the scope's state.
     *
     * @return the mutable state map
     */
    Map<String, Object> state();

    /**
     * Returns the conversation context as a human-readable string, optionally filtered
     * by agent names. Each entry shows the user message and the agent's response.
     *
     * @param agentNames the names of the agents to include; if empty, all agents are included
     * @return the conversation context as a formatted string
     */
    String contextAsConversation(String... agentNames);

    /**
     * Returns the conversation context as a human-readable string, optionally filtered
     * by agent instances. Each entry shows the user message and the agent's response.
     *
     * @param agents the agent instances to include; if empty, all agents are included
     * @return the conversation context as a formatted string
     */
    String contextAsConversation(Object... agents);

    /**
     * Returns all agent invocations recorded in this scope, in execution order.
     *
     * @return an unmodifiable list of all agent invocations
     */
    List<AgentInvocation> agentInvocations();

    /**
     * Returns all agent invocations for the agent with the given name.
     *
     * @param agentName the name of the agent
     * @return a list of invocations matching the agent name
     */
    List<AgentInvocation> agentInvocations(String agentName);

    /**
     * Returns all agent invocations for agents of the given type.
     *
     * @param agentType the class of the agent
     * @return a list of invocations matching the agent type
     */
    List<AgentInvocation> agentInvocations(Class<?> agentType);

    /**
     * Completes a {@link PendingResponse} stored in this scope's state.
     * This is typically called by an external system (e.g., a REST endpoint) to provide
     * a human's response after a process restart or when using a polling/event-driven model.
     *
     * @param responseId the unique identifier of the pending response
     * @param value the value to complete the response with
     * @return {@code true} if a matching pending response was found and completed
     */
    default boolean completePendingResponse(String responseId, Object value) {
        return false;
    }

    /**
     * Returns the identifiers of all {@link PendingResponse} instances stored in this scope's state
     * that have not yet been completed.
     *
     * @return a set of pending response identifiers
     */
    default Set<String> pendingResponseIds() {
        return Set.of();
    }
}
