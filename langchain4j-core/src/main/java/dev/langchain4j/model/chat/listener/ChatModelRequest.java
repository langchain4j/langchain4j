package dev.langchain4j.model.chat.listener;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

/**
 * A request to the {@link ChatLanguageModel} or {@link StreamingChatLanguageModel},
 * intended to be used with {@link ChatModelListener}.
 *
 * @deprecated in favour of {@link ChatRequest}
 */
@Deprecated(forRemoval = true)
public class ChatModelRequest {

    private final String model;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final List<ChatMessage> messages;
    private final List<ToolSpecification> toolSpecifications;

    public ChatModelRequest(String model,
                            Double temperature,
                            Double topP,
                            Integer maxTokens,
                            List<ChatMessage> messages,
                            List<ToolSpecification> toolSpecifications) {
        this.model = model;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.messages = copyIfNotNull(messages);
        this.toolSpecifications = copyIfNotNull(toolSpecifications);
    }

    static ChatModelRequest fromChatRequest(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();
        return ChatModelRequest.builder()
                .model(parameters.modelName())
                .temperature(parameters.temperature())
                .topP(parameters.topP())
                .maxTokens(parameters.maxOutputTokens())
                .messages(chatRequest.messages())
                .toolSpecifications(parameters.toolSpecifications())
                .build();
    }

    static ChatRequest toChatRequest(ChatModelRequest chatModelRequest) {
        return ChatRequest.builder()
                .messages(chatModelRequest.messages())
                .parameters(ChatRequestParameters.builder()
                        .modelName(chatModelRequest.model())
                        .temperature(chatModelRequest.temperature())
                        .topP(chatModelRequest.topP())
                        .maxOutputTokens(chatModelRequest.maxTokens())
                        .toolSpecifications(chatModelRequest.toolSpecifications())
                        .build())
                .build();
    }

    public static ChatModelRequestBuilder builder() {
        return new ChatModelRequestBuilder();
    }

    public String model() {
        return model;
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatModelRequest that = (ChatModelRequest) o;
        return Objects.equals(model, that.model)
                && Objects.equals(temperature, that.temperature)
                && Objects.equals(topP, that.topP)
                && Objects.equals(maxTokens, that.maxTokens)
                && Objects.equals(messages, that.messages)
                && Objects.equals(toolSpecifications, that.toolSpecifications);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, temperature, topP, maxTokens, messages, toolSpecifications);
    }

    public static class ChatModelRequestBuilder {
        private String model;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private List<ChatMessage> messages;
        private List<ToolSpecification> toolSpecifications;

        ChatModelRequestBuilder() {
        }

        public ChatModelRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ChatModelRequestBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public ChatModelRequestBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public ChatModelRequestBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public ChatModelRequestBuilder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public ChatModelRequestBuilder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public ChatModelRequest build() {
            return new ChatModelRequest(this.model, this.temperature, this.topP, this.maxTokens, this.messages, this.toolSpecifications);
        }

        public String toString() {
            return "ChatModelRequest.ChatModelRequestBuilder(model=" + this.model + ", temperature=" + this.temperature + ", topP=" + this.topP + ", maxTokens=" + this.maxTokens + ", messages=" + this.messages + ", toolSpecifications=" + this.toolSpecifications + ")";
        }
    }
}
