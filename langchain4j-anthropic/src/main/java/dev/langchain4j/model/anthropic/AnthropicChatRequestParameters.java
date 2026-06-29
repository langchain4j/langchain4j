package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Objects;

/**
 * Anthropic-specific {@link ChatRequestParameters} that can be set per request (i.e., per {@code ChatRequest}),
 * overriding the values configured on the {@link AnthropicChatModel} / {@link AnthropicStreamingChatModel} builder.
 */
public class AnthropicChatRequestParameters extends DefaultChatRequestParameters {

    public static final AnthropicChatRequestParameters EMPTY =
            AnthropicChatRequestParameters.builder().build();

    private final Boolean cacheSystemMessages;
    private final Boolean cacheTools;
    private final String thinkingType;
    private final Integer thinkingBudgetTokens;
    private final Boolean sendThinking;
    private final Boolean midConversationSystemMessages;
    private final Boolean returnThinking;
    private final String toolChoiceName;
    private final Boolean disableParallelToolUse;
    private final String userId;

    private AnthropicChatRequestParameters(Builder builder) {
        super(builder);
        this.cacheSystemMessages = builder.cacheSystemMessages;
        this.cacheTools = builder.cacheTools;
        this.thinkingType = builder.thinkingType;
        this.thinkingBudgetTokens = builder.thinkingBudgetTokens;
        this.sendThinking = builder.sendThinking;
        this.midConversationSystemMessages = builder.midConversationSystemMessages;
        this.returnThinking = builder.returnThinking;
        this.toolChoiceName = builder.toolChoiceName;
        this.disableParallelToolUse = builder.disableParallelToolUse;
        this.userId = builder.userId;
    }

    public Boolean cacheSystemMessages() {
        return cacheSystemMessages;
    }

    public Boolean cacheTools() {
        return cacheTools;
    }

    public String thinkingType() {
        return thinkingType;
    }

    public Integer thinkingBudgetTokens() {
        return thinkingBudgetTokens;
    }

    public Boolean sendThinking() {
        return sendThinking;
    }

    public Boolean midConversationSystemMessages() {
        return midConversationSystemMessages;
    }

    public Boolean returnThinking() {
        return returnThinking;
    }

    public String toolChoiceName() {
        return toolChoiceName;
    }

    public Boolean disableParallelToolUse() {
        return disableParallelToolUse;
    }

    public String userId() {
        return userId;
    }

    @Override
    public AnthropicChatRequestParameters overrideWith(ChatRequestParameters that) {
        return AnthropicChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public AnthropicChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return AnthropicChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AnthropicChatRequestParameters that = (AnthropicChatRequestParameters) o;
        return Objects.equals(cacheSystemMessages, that.cacheSystemMessages)
                && Objects.equals(cacheTools, that.cacheTools)
                && Objects.equals(thinkingType, that.thinkingType)
                && Objects.equals(thinkingBudgetTokens, that.thinkingBudgetTokens)
                && Objects.equals(sendThinking, that.sendThinking)
                && Objects.equals(midConversationSystemMessages, that.midConversationSystemMessages)
                && Objects.equals(returnThinking, that.returnThinking)
                && Objects.equals(toolChoiceName, that.toolChoiceName)
                && Objects.equals(disableParallelToolUse, that.disableParallelToolUse)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                cacheSystemMessages,
                cacheTools,
                thinkingType,
                thinkingBudgetTokens,
                sendThinking,
                midConversationSystemMessages,
                returnThinking,
                toolChoiceName,
                disableParallelToolUse,
                userId);
    }

    @Override
    public String toString() {
        return "AnthropicChatRequestParameters{"
                + "modelName=" + modelName()
                + ", temperature=" + temperature()
                + ", topP=" + topP()
                + ", topK=" + topK()
                + ", frequencyPenalty=" + frequencyPenalty()
                + ", presencePenalty=" + presencePenalty()
                + ", maxOutputTokens=" + maxOutputTokens()
                + ", stopSequences=" + stopSequences()
                + ", toolSpecifications=" + toolSpecifications()
                + ", toolChoice=" + toolChoice()
                + ", responseFormat=" + responseFormat()
                + ", cacheSystemMessages=" + cacheSystemMessages
                + ", cacheTools=" + cacheTools
                + ", thinkingType=" + thinkingType
                + ", thinkingBudgetTokens=" + thinkingBudgetTokens
                + ", sendThinking=" + sendThinking
                + ", midConversationSystemMessages=" + midConversationSystemMessages
                + ", returnThinking=" + returnThinking
                + ", toolChoiceName=" + toolChoiceName
                + ", disableParallelToolUse=" + disableParallelToolUse
                + ", userId=" + userId
                + '}';
    }

    public Builder toBuilder() {
        return builder().overrideWith(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private Boolean cacheSystemMessages;
        private Boolean cacheTools;
        private String thinkingType;
        private Integer thinkingBudgetTokens;
        private Boolean sendThinking;
        private Boolean midConversationSystemMessages;
        private Boolean returnThinking;
        private String toolChoiceName;
        private Boolean disableParallelToolUse;
        private String userId;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof AnthropicChatRequestParameters anthropicParameters) {
                cacheSystemMessages(getOrDefault(anthropicParameters.cacheSystemMessages(), cacheSystemMessages));
                cacheTools(getOrDefault(anthropicParameters.cacheTools(), cacheTools));
                thinkingType(getOrDefault(anthropicParameters.thinkingType(), thinkingType));
                thinkingBudgetTokens(getOrDefault(anthropicParameters.thinkingBudgetTokens(), thinkingBudgetTokens));
                sendThinking(getOrDefault(anthropicParameters.sendThinking(), sendThinking));
                midConversationSystemMessages(getOrDefault(anthropicParameters.midConversationSystemMessages(), midConversationSystemMessages));
                returnThinking(getOrDefault(anthropicParameters.returnThinking(), returnThinking));
                toolChoiceName(getOrDefault(anthropicParameters.toolChoiceName(), toolChoiceName));
                disableParallelToolUse(
                        getOrDefault(anthropicParameters.disableParallelToolUse(), disableParallelToolUse));
                userId(getOrDefault(anthropicParameters.userId(), userId));
            }
            return this;
        }

        public Builder modelName(AnthropicChatModelName modelName) {
            return super.modelName(modelName == null ? null : modelName.toString());
        }

        public Builder cacheSystemMessages(Boolean cacheSystemMessages) {
            this.cacheSystemMessages = cacheSystemMessages;
            return this;
        }

        public Builder cacheTools(Boolean cacheTools) {
            this.cacheTools = cacheTools;
            return this;
        }

        public Builder thinkingType(String thinkingType) {
            this.thinkingType = thinkingType;
            return this;
        }

        public Builder thinkingBudgetTokens(Integer thinkingBudgetTokens) {
            this.thinkingBudgetTokens = thinkingBudgetTokens;
            return this;
        }

        public Builder sendThinking(Boolean sendThinking) {
            this.sendThinking = sendThinking;
            return this;
        }

        public Builder midConversationSystemMessages(Boolean midConversationSystemMessages) {
            this.midConversationSystemMessages = midConversationSystemMessages;
            return this;
        }

        public Builder returnThinking(Boolean returnThinking) {
            this.returnThinking = returnThinking;
            return this;
        }

        public Builder toolChoiceName(String toolChoiceName) {
            this.toolChoiceName = toolChoiceName;
            return this;
        }

        public Builder disableParallelToolUse(Boolean disableParallelToolUse) {
            this.disableParallelToolUse = disableParallelToolUse;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        @Override
        public AnthropicChatRequestParameters build() {
            return new AnthropicChatRequestParameters(this);
        }
    }
}
