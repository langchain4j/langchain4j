package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.internal.InternalAgent;
import org.a2aproject.sdk.spec.AgentCard;

public interface A2AClientInstance extends InternalAgent {

    String[] inputKeys();

    /**
     * Returns the agent card of the remote agent, fetching it from the A2A server if it has not been fetched yet.
     * The card is normally fetched on the first invocation of the agent, so this call blocks until the fetch
     * completes and can be used to warm up the agent, or to fail fast, before its first invocation.
     *
     * @throws A2AAgentCardException if the card cannot be fetched
     */
    AgentCard agentCard();

    /**
     * Returns the tenant configured for this A2A client instance, or the one of the agent card when the card
     * has already been fetched, or {@code null} otherwise. It never fetches the card. When non-null, this
     * tenant is sent automatically with every message.
     */
    default String tenant() {
        return null;
    }
}
