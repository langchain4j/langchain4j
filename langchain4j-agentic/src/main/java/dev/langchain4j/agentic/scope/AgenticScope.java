package dev.langchain4j.agentic.scope;

import java.util.Map;

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
public interface AgenticScope {

    Object memoryId();

    void writeState(String key, Object value);

    void writeStates(Map<String, Object> newState);

    boolean hasState(String key);

    Object readState(String key);

    <T> T readState(String key, T defaultValue);

    Map<String, Object> state();

    String contextAsConversation(String... agentNames);
    String contextAsConversation(Object... agents);
}
