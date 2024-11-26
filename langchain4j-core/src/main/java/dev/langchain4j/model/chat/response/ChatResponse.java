package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public class ChatResponse {

    private final String id;
    private final String modelName;
    private final AiMessage aiMessage;
    private final TokenUsage tokenUsage;
    private final FinishReason finishReason;

    private ChatResponse(Builder builder) {
        this.id = builder.id;
        this.modelName = builder.modelName;
        this.aiMessage = ensureNotNull(builder.aiMessage, "aiMessage");
        this.tokenUsage = builder.tokenUsage;
        this.finishReason = builder.finishReason;
    }

    public String id() {
        return id;
    }

    public String modelName() {
        return modelName;
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
        return Objects.equals(this.id, that.id)
                && Objects.equals(this.modelName, that.modelName)
                && Objects.equals(this.aiMessage, that.aiMessage)
                && Objects.equals(this.tokenUsage, that.tokenUsage)
                && Objects.equals(this.finishReason, that.finishReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, modelName, aiMessage, tokenUsage, finishReason);
    }

    @Override
    public String toString() {
        return "ChatResponse {" +
                " id = " + quoted(id) +
                ", modelName = " + quoted(modelName) +
                ", aiMessage = " + aiMessage +
                ", tokenUsage = " + tokenUsage +
                ", finishReason = " + finishReason +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String id;
        private String modelName;
        private AiMessage aiMessage;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

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
