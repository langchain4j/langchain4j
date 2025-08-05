package dev.langchain4j.agentic.agent;

public class AgentInvocationException extends RuntimeException {

    public AgentInvocationException(Exception cause) {
        super(cause);
    }

    public AgentInvocationException(String message) {
        super(message);
    }

    public AgentInvocationException(String message, Exception cause) {
        super(message, cause);
    }
}
