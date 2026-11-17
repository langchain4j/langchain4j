package dev.langchain4j.agentic.a2a;

import java.util.Map;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.spec.AgentCard;

/**
 * Configures every A2A client agent, including those declared with
 * {@link dev.langchain4j.agentic.declarative.A2AClientAgent}. It lets a framework apply the same configuration, such
 * as credentials, to all the A2A agents of an application.
 * <p>
 * Implementations are loaded with {@link java.util.ServiceLoader} when an A2A client agent is built, and all of them
 * are applied, in the order they are found.
 */
public interface A2AClientConfigurer {

    /**
     * Returns headers for the request that fetches the Agent Card of an agent. It is called each time the card is
     * fetched, so it can return a refreshed token. The headers of the agent's own
     * {@link dev.langchain4j.agentic.internal.A2AClientBuilder#agentCardHeaders(java.util.function.Supplier)}
     * take precedence over these.
     *
     * @param a2aServerUrl the URL the agent was built with
     * @return the headers to add, or an empty map or {@code null} for none
     */
    default Map<String, String> agentCardHeaders(String a2aServerUrl) {
        return Map.of();
    }

    /**
     * Configures the A2A SDK client of an agent. It runs before the agent's own client customizer, which can override
     * what it configures. When the agent has no client customizer, the default JSON-RPC transport is configured before
     * it.
     *
     * @param a2aServerUrl the URL the agent was built with
     * @param agentCard the agent's Agent Card, for example to choose credentials by agent name; the
     *     {@link ClientBuilder} does not expose it
     * @param clientBuilder the builder of the agent's A2A SDK client
     */
    default void customize(String a2aServerUrl, AgentCard agentCard, ClientBuilder clientBuilder) {}
}
