package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Objects;

@Internal
public class ToolServiceResult {

    private final List<ChatResponse> intermediateResponses;
    private final ChatResponse finalResponse;
    private final List<ToolExecution> toolExecutions;
    private final TokenUsage aggregateTokenUsage;
    private final boolean immediateToolReturn;
    private final boolean suspended;
    private final List<ToolExecutionRequest> pendingToolExecutionRequests;

    /**
     * @since 1.2.0
     */
    public ToolServiceResult(Builder builder) {
        this.intermediateResponses = copy(builder.intermediateResponses);
        this.finalResponse = ensureNotNull(builder.finalResponse, "finalResponse");
        this.toolExecutions = ensureNotNull(builder.toolExecutions, "toolExecutions");
        this.aggregateTokenUsage = builder.aggregateTokenUsage;
        this.immediateToolReturn = builder.immediateToolReturn;
        this.suspended = builder.suspended;
        this.pendingToolExecutionRequests = copy(builder.pendingToolExecutionRequests);
    }

    /**
     * @deprecated Please use {@link #ToolServiceResult(Builder)} instead
     */
    @Deprecated(since = "1.2.0")
    public ToolServiceResult(ChatResponse chatResponse, List<ToolExecution> toolExecutions) {
        this.intermediateResponses = List.of();
        this.finalResponse = ensureNotNull(chatResponse, "chatResponse");
        this.toolExecutions = ensureNotNull(toolExecutions, "toolExecutions");
        this.aggregateTokenUsage = chatResponse.tokenUsage();
        this.immediateToolReturn = false;
        this.suspended = false;
        this.pendingToolExecutionRequests = List.of();
    }

    /**
     * @since 1.2.0
     */
    public List<ChatResponse> intermediateResponses() {
        return intermediateResponses;
    }

    /**
     * @since 1.2.0
     */
    public ChatResponse finalResponse() {
        return finalResponse;
    }

    /**
     * @since 1.2.0
     */
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
    @Deprecated(since = "1.2.0")
    public ChatResponse chatResponse() {
        return aggregateResponse();
    }

    public List<ToolExecution> toolExecutions() {
        return toolExecutions;
    }

    /**
     * @since 1.2.0
     */
    public TokenUsage aggregateTokenUsage() {
        return aggregateTokenUsage;
    }

    /**
     * @since 1.4.0
     */
    public boolean immediateToolReturn() {
        return immediateToolReturn;
    }

    /**
     * Whether the loop was suspended by a {@link dev.langchain4j.agent.tool.ReturnBehavior#SUSPEND} tool.
     *
     * @since 1.18.0
     */
    public boolean suspended() {
        return suspended;
    }

    /**
     * The tool calls left pending when the loop {@linkplain #suspended() suspended}; empty otherwise.
     *
     * @since 1.18.0
     */
    public List<ToolExecutionRequest> pendingToolExecutionRequests() {
        return pendingToolExecutionRequests;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ToolServiceResult) obj;
        return Objects.equals(this.intermediateResponses, that.intermediateResponses)
                && Objects.equals(this.finalResponse, that.finalResponse)
                && Objects.equals(this.toolExecutions, that.toolExecutions)
                && Objects.equals(this.aggregateTokenUsage, that.aggregateTokenUsage)
                && Objects.equals(this.immediateToolReturn, that.immediateToolReturn)
                && Objects.equals(this.suspended, that.suspended)
                && Objects.equals(this.pendingToolExecutionRequests, that.pendingToolExecutionRequests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                intermediateResponses,
                finalResponse,
                toolExecutions,
                aggregateTokenUsage,
                immediateToolReturn,
                suspended,
                pendingToolExecutionRequests);
    }

    @Override
    public String toString() {
        return "ToolServiceResult{" + "intermediateResponses="
                + intermediateResponses + ", finalResponse="
                + finalResponse + ", toolExecutions="
                + toolExecutions + ", aggregateTokenUsage="
                + aggregateTokenUsage + ", immediateToolReturn="
                + immediateToolReturn + ", suspended="
                + suspended + ", pendingToolExecutionRequests="
                + pendingToolExecutionRequests + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<ChatResponse> intermediateResponses;
        private ChatResponse finalResponse;
        private List<ToolExecution> toolExecutions;
        private TokenUsage aggregateTokenUsage;
        private boolean immediateToolReturn;
        private boolean suspended;
        private List<ToolExecutionRequest> pendingToolExecutionRequests;

        public Builder intermediateResponses(List<ChatResponse> intermediateResponses) {
            this.intermediateResponses = intermediateResponses;
            return this;
        }

        public Builder finalResponse(ChatResponse finalResponse) {
            this.finalResponse = finalResponse;
            return this;
        }

        public Builder toolExecutions(List<ToolExecution> toolExecutions) {
            this.toolExecutions = toolExecutions;
            return this;
        }

        public Builder aggregateTokenUsage(TokenUsage aggregateTokenUsage) {
            this.aggregateTokenUsage = aggregateTokenUsage;
            return this;
        }

        public Builder immediateToolReturn(boolean immediateToolReturn) {
            this.immediateToolReturn = immediateToolReturn;
            return this;
        }

        /**
         * @since 1.18.0
         */
        public Builder suspended(boolean suspended) {
            this.suspended = suspended;
            return this;
        }

        /**
         * @since 1.18.0
         */
        public Builder pendingToolExecutionRequests(List<ToolExecutionRequest> pendingToolExecutionRequests) {
            this.pendingToolExecutionRequests = pendingToolExecutionRequests;
            return this;
        }

        public ToolServiceResult build() {
            return new ToolServiceResult(this);
        }
    }
}
