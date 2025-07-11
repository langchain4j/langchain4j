package dev.langchain4j.service.tool;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Internal
public class ToolServiceResult {

    private final List<ChatResponse> intermediateResponses;
    private final ChatResponse finalResponse;
    private final List<ToolExecution> toolExecutions;
    private final TokenUsage aggregateTokenUsage;

    public ToolServiceResult(List<ChatResponse> intermediateResponses,
                             ChatResponse finalResponse,
                             List<ToolExecution> toolExecutions,
                             TokenUsage aggregateTokenUsage) {
        this.intermediateResponses = copy(intermediateResponses);
        this.finalResponse = ensureNotNull(finalResponse, "finalResponse");
        this.toolExecutions = ensureNotNull(toolExecutions, "toolExecutions");
        this.aggregateTokenUsage = aggregateTokenUsage;
    }

    /**
     * @deprecated Please use {@link #ToolServiceResult(List, ChatResponse, List, TokenUsage)} instead
     */
    @Deprecated
    public ToolServiceResult(ChatResponse chatResponse,
                             List<ToolExecution> toolExecutions) {
        this.intermediateResponses = List.of();
        this.finalResponse = ensureNotNull(chatResponse, "chatResponse"); // TODO recalculate tokens
        this.toolExecutions = ensureNotNull(toolExecutions, "toolExecutions");
        this.aggregateTokenUsage = chatResponse.tokenUsage();
    }

    public List<ChatResponse> intermediateResponses() {
        return intermediateResponses;
    }

    public ChatResponse finalResponse() {
        return finalResponse;
    }

    public ChatResponse aggregateResponse() {
        return ChatResponse.builder()
                .aiMessage(finalResponse.aiMessage())
                .metadata(finalResponse.metadata().toBuilder()
                        .tokenUsage(aggregateTokenUsage)
                        .build())
                .build();
    }

    /**
     * @deprecated Please use {@link #aggregateResponse()} instead for clarity
     */
    @Deprecated
    public ChatResponse chatResponse() {
        return aggregateResponse();
    }

    public List<ToolExecution> toolExecutions() {
        return toolExecutions;
    }

    public TokenUsage aggregateTokenUsage() {
        return aggregateTokenUsage;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ToolServiceResult) obj;
        return Objects.equals(this.intermediateResponses, that.intermediateResponses)
                && Objects.equals(this.finalResponse, that.finalResponse)
                && Objects.equals(this.toolExecutions, that.toolExecutions)
                && Objects.equals(this.aggregateTokenUsage, that.aggregateTokenUsage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intermediateResponses, finalResponse, toolExecutions, aggregateTokenUsage);
    }

    @Override
    public String toString() {
        return "ToolServiceResult{" +
                "intermediateResponses=" + intermediateResponses +
                ", finalResponse=" + finalResponse +
                ", toolExecutions=" + toolExecutions +
                ", aggregateTokenUsage=" + aggregateTokenUsage +
                '}';
    }
}
