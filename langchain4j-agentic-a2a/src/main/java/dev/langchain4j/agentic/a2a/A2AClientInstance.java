package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.internal.InternalAgent;
import org.a2aproject.sdk.spec.AgentCard;

public interface A2AClientInstance extends InternalAgent {

    String[] inputKeys();

    AgentCard agentCard();

    /**
     * Returns the tenant associated with this A2A client instance, or {@code null}
     * if no tenant was configured. When non-null, this tenant is sent automatically
     * with every message.
     */
    default String tenant() {
        return null;
    }
}
