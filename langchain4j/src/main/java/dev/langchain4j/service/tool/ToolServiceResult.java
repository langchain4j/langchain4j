package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.List;
import java.util.Objects;
import dev.langchain4j.Internal;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

@Internal
public class ToolServiceResult {

    private final List<ChatResponse> intermediateResponses;
    private final ChatResponse finalResponse;
    private final List<ToolExecution> toolExecutions;
    private final TokenUsage aggregateTokenUsage;
    private final boolean immediateToolReturn;
    private final List<ChatMessage> messages;

    /**
     * @since 1.2.0
     */
    public ToolServiceResult(Builder builder) {
        this.intermediateResponses = copy(builder.intermediateResponses);
        this.finalResponse = ensureNotNull(builder.finalResponse, "finalResponse");
        this.toolExecutions = ensureNotNull(builder.toolExecutions, "toolExecutions");
        this.aggregateTokenUsage = builder.aggregateTokenUsage;
        this.immediateToolReturn = builder.immediateToolReturn;
        this.messages = copy(builder.messages);
    }

    /**
     * @deprecated Please use {@link #ToolServiceResult(Builder)} instead
     */
    @Deprecated(since = "1.2.0")
    public ToolServiceResult(ChatResponse chatResponse,
                             List<ToolExecution> toolExecutions) {
        this.intermediateResponses = List.of();
        this.finalResponse = ensureNotNull(chatResponse, "chatResponse");
        this.toolExecutions = ensureNotNull(toolExecutions, "toolExecutions");
        this.aggregateTokenUsage = chatResponse.tokenUsage();
        this.immediateToolReturn = false;
        this.messages = List.of();
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
     * The conversation the tool loop ended with (the messages sent, every tool round trip and the final response),
     * when the AI Service has no chat memory. Empty when it has one: the chat memory holds the conversation then.
     *
     * @since 1.23.0
     */
    public List<ChatMessage> messages() {
        return messages;
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
                && Objects.equals(this.messages, that.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                intermediateResponses,
                finalResponse,
                toolExecutions,
                aggregateTokenUsage,
                immediateToolReturn,
                messages);
    }

    @Override
    public String toString() {
        return "ToolServiceResult{" +
                "intermediateResponses=" + intermediateResponses +
                ", finalResponse=" + finalResponse +
                ", toolExecutions=" + toolExecutions +
                ", aggregateTokenUsage=" + aggregateTokenUsage +
                ", immediateToolReturn=" + immediateToolReturn +
                ", messages=" + messages +
                '}';
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
        private List<ChatMessage> messages;

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
         * @since 1.23.0
         */
        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public ToolServiceResult build() {
            return new ToolServiceResult(this);
        }
    }
}
