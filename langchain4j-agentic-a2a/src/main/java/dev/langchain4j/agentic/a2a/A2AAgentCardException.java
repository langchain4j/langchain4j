package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.agent.AgentUnavailableException;
import dev.langchain4j.agentic.internal.LazyResolution;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Thrown when the agent card of a remote A2A agent cannot be fetched. The card is fetched lazily, the first time
 * it is needed, which is normally the first invocation of the agent, so this exception reaches the caller of that
 * invocation and the error handler of the agentic system, where it can be recognized to skip or retry an
 * unavailable remote agent, by this type or by its supertype {@link AgentUnavailableException}, common to every
 * integration. A fetch that does not complete within the resolution timeout of the agent, 30 seconds by default, is
 * interrupted and reported with this exception too, with a {@link TimeoutException} as cause. Concurrent first
 * invocations wait on the same fetch and all fail when it fails, each with its own exception sharing that cause,
 * but a failed fetch is not cached: the next invocation retries it.
 *
 * @since 1.24.0
 */
public class A2AAgentCardException extends AgentUnavailableException {

    private final String a2aServerUrl;

    public A2AAgentCardException(String agentName, String a2aServerUrl, Throwable cause) {
        super(
                agentName,
                "Failed to fetch the agent card of A2A agent '" + agentName + "' from " + withoutCredentials(a2aServerUrl),
                cause);
        this.a2aServerUrl = a2aServerUrl;
    }

    /**
     * Creates the exception reporting that the agent card was not fetched within the resolution timeout.
     *
     * @since 1.24.0
     */
    public A2AAgentCardException(String agentName, String a2aServerUrl, Duration timeout, TimeoutException cause) {
        super(
                agentName,
                "Failed to fetch the agent card of A2A agent '" + agentName + "' from "
                        + withoutCredentials(a2aServerUrl) + " within " + LazyResolution.describe(timeout),
                cause);
        this.a2aServerUrl = a2aServerUrl;
    }

    private static String withoutCredentials(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null) {
                return "a malformed URL";
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (URISyntaxException e) {
            return "a malformed URL";
        }
    }

    /**
     * @return the URL of the A2A server the card was requested from, as configured; the message of this exception
     * shows it without user info and query, which may contain credentials
     * @since 1.24.0
     */
    public String a2aServerUrl() {
        return a2aServerUrl;
    }
}
