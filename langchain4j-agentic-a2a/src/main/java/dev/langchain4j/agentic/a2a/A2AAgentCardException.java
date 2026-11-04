package dev.langchain4j.agentic.a2a;

import dev.langchain4j.exception.LangChain4jException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Thrown when the agent card of a remote A2A agent cannot be fetched. The card is fetched lazily, the first time
 * it is needed, which is normally the first invocation of the agent, so this exception reaches the caller of that
 * invocation and the error handler of the agentic system, where it can be recognized to skip or retry an
 * unavailable remote agent. A failed fetch is not cached: the next invocation retries it.
 *
 * @since 1.24.0
 */
public class A2AAgentCardException extends LangChain4jException {

    private final String agentName;
    private final String a2aServerUrl;

    public A2AAgentCardException(String agentName, String a2aServerUrl, Throwable cause) {
        super(
                "Failed to fetch the agent card of A2A agent '" + agentName + "' from " + withoutCredentials(a2aServerUrl),
                cause);
        this.agentName = agentName;
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
     * @return the name of the A2A agent whose card could not be fetched
     * @since 1.24.0
     */
    public String agentName() {
        return agentName;
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
