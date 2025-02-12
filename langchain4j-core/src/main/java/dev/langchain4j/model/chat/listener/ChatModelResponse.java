package dev.langchain4j.model.chat.listener;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Objects;

/**
 * A response from the {@link ChatLanguageModel} or {@link StreamingChatLanguageModel},
 * intended to be used with {@link ChatModelListener}.
 *
 * @deprecated in favour of {@link ChatResponse}
 */
@Deprecated(forRemoval = true)
public class ChatModelResponse {

    private final String id;
    private final String model;
    private final TokenUsage tokenUsage;
    private final FinishReason finishReason;
    private final AiMessage aiMessage;

    public ChatModelResponse(String id,
                             String model,
                             TokenUsage tokenUsage,
                             FinishReason finishReason,
                             AiMessage aiMessage) {
        this.id = id;
        this.model = model;
        this.tokenUsage = tokenUsage;
        this.finishReason = finishReason;
        this.aiMessage = aiMessage;
    }

    static ChatModelResponse fromChatResponse(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }

        ChatResponseMetadata chatResponseMetadata = chatResponse.metadata();
        return ChatModelResponse.builder()
                .id(chatResponseMetadata.id())
                .model(chatResponseMetadata.modelName())
                .tokenUsage(chatResponseMetadata.tokenUsage())
                .finishReason(chatResponseMetadata.finishReason())
                .aiMessage(chatResponse.aiMessage())
                .build();
    }

    static ChatResponse toChatResponse(ChatModelResponse chatModelResponse) {
        if (chatModelResponse == null) {
            return null;
        }

        return ChatResponse.builder()
                .aiMessage(chatModelResponse.aiMessage())
                .metadata(ChatResponseMetadata.builder()
                        .id(chatModelResponse.id())
                        .modelName(chatModelResponse.model())
                        .tokenUsage(chatModelResponse.tokenUsage())
                        .finishReason(chatModelResponse.finishReason())
                        .build())
                .build();
    }

    public static ChatModelResponseBuilder builder() {
        return new ChatModelResponseBuilder();
    }

    public String id() {
        return id;
    }

    public String model() {
        return model;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public FinishReason finishReason() {
        return finishReason;
    }

    public AiMessage aiMessage() {
        return aiMessage;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatModelResponse that = (ChatModelResponse) o;
        return Objects.equals(id, that.id)
                && Objects.equals(model, that.model)
                && Objects.equals(tokenUsage, that.tokenUsage)
                && finishReason == that.finishReason
                && Objects.equals(aiMessage, that.aiMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, model, tokenUsage, finishReason, aiMessage);
    }

    public static class ChatModelResponseBuilder {
        private String id;
        private String model;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;
        private AiMessage aiMessage;

        ChatModelResponseBuilder() {
        }

        public ChatModelResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public ChatModelResponseBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ChatModelResponseBuilder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        public ChatModelResponseBuilder finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public ChatModelResponseBuilder aiMessage(AiMessage aiMessage) {
            this.aiMessage = aiMessage;
            return this;
        }

        public ChatModelResponse build() {
            return new ChatModelResponse(this.id, this.model, this.tokenUsage, this.finishReason, this.aiMessage);
        }

        public String toString() {
            return "ChatModelResponse.ChatModelResponseBuilder(id=" + this.id + ", model=" + this.model + ", tokenUsage=" + this.tokenUsage + ", finishReason=" + this.finishReason + ", aiMessage=" + this.aiMessage + ")";
        }
    }
}
