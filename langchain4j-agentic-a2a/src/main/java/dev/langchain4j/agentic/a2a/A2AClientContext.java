package dev.langchain4j.agentic.a2a;

import org.a2aproject.sdk.spec.AgentCard;

/**
 * The A2A client agent an {@link A2AClientConfigurator} is applied to.
 *
 * @param a2aServerUrl the URL the agent was built with
 * @param tenant the tenant of the request: the tenant of the call for messages, otherwise the agent's configured
 *     tenant; {@code null} when there is none
 * @param agentName the name of the local agent
 * @param agentInterface the interface the agent was built from
 * @param agentCard the remote agent's Agent Card, or {@code null} while it is being fetched
 */
public record A2AClientContext(
        String a2aServerUrl, String tenant, String agentName, Class<?> agentInterface, AgentCard agentCard) {}
