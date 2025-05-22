package dev.langchain4j.model.chat.response;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Objects;

public class ChatResponse {

    private final AiMessage aiMessage;
    private final ChatResponseMetadata metadata;

    protected ChatResponse(Builder builder) {
        this.aiMessage = ensureNotNull(builder.aiMessage, "aiMessage");

        ChatResponseMetadata.Builder<?> metadataBuilder = ChatResponseMetadata.builder();
        if (builder.id != null) {
            validate(builder, "id");
            metadataBuilder.id(builder.id);
        }
        if (builder.modelName != null) {
            validate(builder, "modelName");
            metadataBuilder.modelName(builder.modelName);
        }
        if (builder.tokenUsage != null) {
            validate(builder, "tokenUsage");
            metadataBuilder.tokenUsage(builder.tokenUsage);
        }
        if (builder.finishReason != null) {
            validate(builder, "finishReason");
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

    /**
     * Converts the current instance of {@code ChatResponse} into a {@link Builder},
     * allowing modifications to the current object's fields.
     *
     * @return a new {@link Builder} instance initialized with the current state of this {@code ChatResponse}.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    public ChatResponseMetadata metadata() {
        return metadata;
    }

    public String id() {
        return metadata.id();
    }

    public String modelName() {
        return metadata.modelName();
    }

    public TokenUsage tokenUsage() {
        return metadata.tokenUsage();
    }

    public FinishReason finishReason() {
        return metadata.finishReason();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatResponse that = (ChatResponse) o;
        return Objects.equals(this.aiMessage, that.aiMessage) && Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiMessage, metadata);
    }

    @Override
    public String toString() {
        return "ChatResponse {" + " aiMessage = " + aiMessage + ", metadata = " + metadata + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AiMessage aiMessage;
        private ChatResponseMetadata metadata;

        private String id;
        private String modelName;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;

        public Builder() {}

        public Builder(ChatResponse chatResponse) {
            this.aiMessage = chatResponse.aiMessage;
            this.metadata = chatResponse.metadata;
        }

        public Builder aiMessage(AiMessage aiMessage) {
            this.aiMessage = aiMessage;
            return this;
        }

        public Builder metadata(ChatResponseMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
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

    private static void validate(Builder builder, String name) {
        if (builder.metadata != null) {
            throw new IllegalArgumentException("Cannot set both 'metadata' and '%s' on ChatResponse".formatted(name));
        }
    }
}
