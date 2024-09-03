package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public class ChatResponse {

    private final AiMessage aiMessage;
    private final TokenUsage tokenUsage;
    private final FinishReason finishReason;

    private ChatResponse(Builder builder) {
        this.aiMessage = ensureNotNull(builder.aiMessage, "aiMessage");
        this.tokenUsage = builder.tokenUsage;
        this.finishReason = builder.finishReason;
    }

    public AiMessage aiMessage() {
        return aiMessage;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public FinishReason finishReason() {
        return finishReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatResponse that = (ChatResponse) o;
        return Objects.equals(this.aiMessage, that.aiMessage)
                && Objects.equals(this.tokenUsage, that.tokenUsage)
                && Objects.equals(this.finishReason, that.finishReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiMessage, tokenUsage, finishReason);
    }

    @Override
    public String toString() {
        return "ChatResponse {" +
                " aiMessage = " + aiMessage +
                ", tokenUsage = " + tokenUsage +
                ", finishReason = " + finishReason +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private AiMessage aiMessage;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;

        public Builder aiMessage(AiMessage aiMessage) {
            this.aiMessage = aiMessage;
            return this;
        }

        public Builder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        public Builder finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public ChatResponse build() {
            return new ChatResponse(this);
        }
    }
}
