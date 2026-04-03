package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Internal
public class ToolServiceResult {

    private final List<ChatResponse> intermediateResponses;
    private final ChatResponse finalResponse;
    private final List<ToolExecution> toolExecutions;
    private final TokenUsage aggregateTokenUsage;
    private final boolean immediateToolReturn;
    private final Map<String, Integer> toolInvocationCounts;

    /**
     * @since 1.2.0
     */
    public ToolServiceResult(Builder builder) {
        this.intermediateResponses = copy(builder.intermediateResponses);
        this.finalResponse = ensureNotNull(builder.finalResponse, "finalResponse");
        this.toolExecutions = ensureNotNull(builder.toolExecutions, "toolExecutions");
        this.aggregateTokenUsage = builder.aggregateTokenUsage;
        this.immediateToolReturn = builder.immediateToolReturn;
        this.toolInvocationCounts =
                builder.toolInvocationCounts == null ? Map.of() : Map.copyOf(builder.toolInvocationCounts);
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
        this.toolInvocationCounts = Map.of();
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
     * Returns the number of times each tool was invoked during this AI service call.
     *
     * @return an unmodifiable map from tool name to invocation count
     * @since 1.14.0
     */
    public Map<String, Integer> toolInvocationCounts() {
        return toolInvocationCounts;
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
                && Objects.equals(this.toolInvocationCounts, that.toolInvocationCounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                intermediateResponses,
                finalResponse,
                toolExecutions,
                aggregateTokenUsage,
                immediateToolReturn,
                toolInvocationCounts);
    }

    @Override
    public String toString() {
        return "ToolServiceResult{" + "intermediateResponses="
                + intermediateResponses + ", finalResponse="
                + finalResponse + ", toolExecutions="
                + toolExecutions + ", aggregateTokenUsage="
                + aggregateTokenUsage + ", immediateToolReturn="
                + immediateToolReturn + ", toolInvocationCounts="
                + toolInvocationCounts + '}';
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
        private Map<String, Integer> toolInvocationCounts;

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
         * @since 1.14.0
         */
        public Builder toolInvocationCounts(Map<String, Integer> toolInvocationCounts) {
            this.toolInvocationCounts = toolInvocationCounts;
            return this;
        }

        public ToolServiceResult build() {
            return new ToolServiceResult(this);
        }
    }
}
