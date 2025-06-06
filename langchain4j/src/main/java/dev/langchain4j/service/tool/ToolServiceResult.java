package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Internal
public class ToolServiceResult {

    private final ChatResponse chatResponse;
    private final List<ToolExecution> toolExecutions;

    public ToolServiceResult(ChatResponse chatResponse,
                             List<ToolExecution> toolExecutions) {
        this.chatResponse = ensureNotNull(chatResponse, "chatResponse");
        this.toolExecutions = ensureNotNull(toolExecutions, "toolExecutions");
    }

    public ChatResponse chatResponse() {
        return chatResponse;
    }

    public List<ToolExecution> toolExecutions() {
        return toolExecutions;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ToolServiceResult) obj;
        return Objects.equals(this.chatResponse, that.chatResponse) &&
                Objects.equals(this.toolExecutions, that.toolExecutions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatResponse, toolExecutions);
    }

    @Override
    public String toString() {
        return "ToolServiceResult{" +
                "chatResponse=" + chatResponse +
                ", toolExecutions=" + toolExecutions +
                '}';
    }
}
