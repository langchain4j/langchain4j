package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public class ChatResponse {

    private final AiMessage aiMessage;
    private final ChatResponseMetadata metadata;

    protected ChatResponse(@NonNull Builder builder) {
        this.aiMessage = ensureNotNull(builder.aiMessage, "aiMessage");

        ChatResponseMetadata.Builder<?> metadataBuilder = ChatResponseMetadata.builder();

        if (builder.tokenUsage != null) {
            if (builder.metadata != null) {
                throw new IllegalArgumentException(
                        "Cannot set both 'metadata' and 'tokenUsage' on ChatResponse");
            }
            metadataBuilder.tokenUsage(builder.tokenUsage);
        }

        if (builder.finishReason != null) {
            if (builder.metadata != null) {
                throw new IllegalArgumentException(
                        "Cannot set both 'metadata' and 'finishReason' on ChatResponse");
            }
            metadataBuilder.finishReason(builder.finishReason);
        }

        if (builder.metadata != null) {
            this.metadata = builder.metadata;
        } else {
            this.metadata = metadataBuilder.build();
        }
    }

    public AiMessage aiMessage() {
        return aiMessage;
    }

    @Experimental
    public ChatResponseMetadata metadata() {
        return metadata;
    }

    // TODO deprecate
    public TokenUsage tokenUsage() {
        return metadata.tokenUsage();
    }

    // TODO deprecate
    public FinishReason finishReason() {
        return metadata.finishReason();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatResponse that = (ChatResponse) o;
        return Objects.equals(this.aiMessage, that.aiMessage)
                && Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiMessage, metadata);
    }

    @Override
    public String toString() {
        return "ChatResponse {" +
                " aiMessage = " + aiMessage +
                ", metadata = " + metadata +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private AiMessage aiMessage;
        private ChatResponseMetadata metadata;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;

        public Builder aiMessage(AiMessage aiMessage) {
            this.aiMessage = aiMessage;
            return this;
        }

        public Builder metadata(ChatResponseMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        // TODO deprecate
        public Builder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        // TODO deprecate
        public Builder finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public ChatResponse build() {
            return new ChatResponse(this);
        }
    }
}
