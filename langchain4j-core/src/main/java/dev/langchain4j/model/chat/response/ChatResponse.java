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
    // TODO name
    // TODO place
    // TODO structure

    // TODO custom model provider stuff (system_fingerprint, service_tier, created, logprobs?)
    // TODO return raw response? as a json string?

    private final String id;
    private final String modelName;
    private final AiMessage aiMessage;
    private final TokenUsage tokenUsage; // TODO custom TokenUsage
    private final FinishReason finishReason;

    protected ChatResponse(Builder builder) { // TODO
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

    public static Builder builder() { // TODO
        return new Builder();
    }

    public static class Builder<T extends Builder<T>> {

        private String id;
        private String modelName;
        private AiMessage aiMessage;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;

        public T id(String id) {
            this.id = id;
            return (T) this;
        }

        public T modelName(String modelName) {
            this.modelName = modelName;
            return (T) this;
        }

        public T aiMessage(AiMessage aiMessage) {
            this.aiMessage = aiMessage;
            return (T) this;
        }

        public T tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return (T) this;
        }

        public T finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
            return (T) this;
        }

        public ChatResponse build() {
            return new ChatResponse(this);
        }
    }
}
