package dev.langchain4j.agentic.agent;

public class MissingArgumentException extends AgentInvocationException {

    private final String argumentName;

    public MissingArgumentException(String argumentName) {
        super("Missing argument: " + argumentName);
        this.argumentName = argumentName;
    }

    public String argumentName() {
        return argumentName;
    }
}
