package dev.langchain4j.agentic.a2a;

import java.util.Map;
import org.a2aproject.sdk.client.ClientBuilder;

/**
 * Configures every A2A client agent, including those declared with
 * {@link dev.langchain4j.agentic.declarative.A2AClientAgent}. It lets a framework apply the same configuration, such
 * as credentials, to all the A2A agents of an application.
 * <p>
 * Implementations are loaded with {@link java.util.ServiceLoader} when an A2A client agent is built, and all of them
 * are applied, in the order they are found. Headers returned by several configurators are merged; when two return
 * the same header, the one found later wins.
 * <p>
 * Prefer {@link #agentCardHeaders(A2AClientContext)} and {@link #messageHeaders(A2AClientContext)} for credentials:
 * they are sent with each request, on any transport, and an agent's own client customizer cannot remove them.
 */
public interface A2AClientConfigurator {

    /**
     * Returns headers for the request that fetches the Agent Card of an agent. It is called each time the card is
     * fetched, so it can return a refreshed token. The headers of the agent's own
     * {@link dev.langchain4j.agentic.internal.A2AClientBuilder#agentCardHeaders(java.util.function.Supplier)}
     * take precedence over these.
     *
     * @param context the agent being configured; its {@link A2AClientContext#agentCard() Agent Card} is not known
     *     yet
     * @return the headers to add, or an empty map or {@code null} for none
     */
    default Map<String, String> agentCardHeaders(A2AClientContext context) {
        return Map.of();
    }

    /**
     * Returns headers for each message sent to an agent. It is called for every message, so it can return a
     * refreshed token. The headers are sent with the call itself, so they apply to any transport and are kept when
     * the agent's own client customizer configures a transport.
     *
     * @param context the agent being called, with its Agent Card and the tenant of this call
     * @return the headers to add, or an empty map or {@code null} for none
     */
    default Map<String, String> messageHeaders(A2AClientContext context) {
        return Map.of();
    }

    /**
     * Configures the A2A SDK client of an agent, for what headers cannot do. It runs before the agent's own client
     * customizer. Note that {@link ClientBuilder#withTransport} replaces the configuration of a transport, so a
     * customizer that configures the same transport replaces what is set here; use the header methods for
     * credentials.
     *
     * @param context the agent being configured, with its Agent Card
     * @param clientBuilder the builder of the agent's A2A SDK client
     */
    default void customize(A2AClientContext context, ClientBuilder clientBuilder) {}
}
