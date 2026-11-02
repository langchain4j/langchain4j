package dev.langchain4j.agentic.a2a;

/**
 * Thrown when the agent card of a remote A2A agent cannot be fetched. The card is fetched lazily, the first time
 * it is needed, which is normally the first invocation of the agent, so this exception reaches the caller of that
 * invocation and the error handler of the agentic system, where it can be recognized to skip or retry an
 * unavailable remote agent. A failed fetch is not cached: the next invocation retries it.
 */
public class A2AAgentCardException extends RuntimeException {

    private final String agentName;
    private final String a2aServerUrl;

    public A2AAgentCardException(String agentName, String a2aServerUrl, Throwable cause) {
        super("Failed to fetch the agent card of A2A agent '" + agentName + "' from " + a2aServerUrl, cause);
        this.agentName = agentName;
        this.a2aServerUrl = a2aServerUrl;
    }

    /**
     * @return the name of the A2A agent whose card could not be fetched
     */
    public String agentName() {
        return agentName;
    }

    /**
     * @return the URL of the A2A server the card was requested from
     */
    public String a2aServerUrl() {
        return a2aServerUrl;
    }
}
