package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Objects;

@Internal
public class ToolServiceResult {

    private final ChatResponse chatResponse;
    private final List<ToolExecution> toolExecutions;
    private final TokenUsage tokenUsageAccumulator;

    public ToolServiceResult(ChatResponse chatResponse,
                             List<ToolExecution> toolExecutions,
                             TokenUsage tokenUsageAccumulator) {
        this.chatResponse = chatResponse;
        this.toolExecutions = toolExecutions;
        this.tokenUsageAccumulator = tokenUsageAccumulator;
    }

    public ChatResponse chatResponse() {
        return chatResponse;
    }

    public List<ToolExecution> toolExecutions() {
        return toolExecutions;
    }

    public TokenUsage tokenUsageAccumulator() {
        return tokenUsageAccumulator;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ToolServiceResult) obj;
        return Objects.equals(this.chatResponse, that.chatResponse) &&
                Objects.equals(this.toolExecutions, that.toolExecutions) &&
                Objects.equals(this.tokenUsageAccumulator, that.tokenUsageAccumulator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatResponse, toolExecutions, tokenUsageAccumulator);
    }

    @Override
    public String toString() {
        return "ToolServiceResult[" +
                "chatResponse=" + chatResponse + ", " +
                "toolExecutions=" + toolExecutions + ", " +
                "tokenUsageAccumulator=" + tokenUsageAccumulator + ']';
    }
}
