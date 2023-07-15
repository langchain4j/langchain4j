package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.Objects;

public class AiMessage extends ChatMessage {

    private final ToolExecutionRequest toolExecutionRequest;

    public AiMessage(String text) {
        this(text, null);
    }

    public AiMessage(String text, ToolExecutionRequest toolExecutionRequest) {
        super(text);
        this.toolExecutionRequest = toolExecutionRequest;
    }

    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiMessage that = (AiMessage) o;
        return Objects.equals(this.text, that.text)
                && Objects.equals(this.toolExecutionRequest, that.toolExecutionRequest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, toolExecutionRequest);
    }

    @Override
    public String toString() {
        return "AiMessage {" +
                " text = \"" + text + "\"" +
                " toolExecutionRequest = \"" + toolExecutionRequest + "\"" +
                " }";
    }

    public static AiMessage from(String text) {
        return new AiMessage(text);
    }

    public static AiMessage from(ToolExecutionRequest toolExecutionRequest) {
        return new AiMessage(null, toolExecutionRequest);
    }

    public static AiMessage aiMessage(String text) {
        return from(text);
    }

    public static AiMessage aiMessage(ToolExecutionRequest toolExecutionRequest) {
        return from(toolExecutionRequest);
    }
}
