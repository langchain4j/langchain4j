package dev.langchain4j.model.novitaai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.Exceptions;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.novitaai.client.NovitaAiChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Message;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.novitaai.client.AbstractNovitaAIModel;
import dev.langchain4j.model.novitaai.client.NovitaAiChatCompletionRequest;
import dev.langchain4j.model.novitaai.spi.NovitaAiChatModelBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.novitaai.mapper.NovitaAiMapper.aiMessageFrom;
import static dev.langchain4j.model.novitaai.mapper.NovitaAiMapper.finishReasonFrom;
import static dev.langchain4j.model.novitaai.mapper.NovitaAiMapper.toException;
import static dev.langchain4j.model.novitaai.mapper.NovitaAiMapper.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * NovitaAI Chat model.
 */
@Slf4j
public class NovitaAiChatModel extends AbstractNovitaAIModel implements ChatLanguageModel {

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      builder.
     */
    public NovitaAiChatModel(Builder builder) {
        this(builder.modelName.getModelId(), builder.apiKey);
    }

    /**
     * Constructor with Builder.
     *
     * @param modelName
     *      model name
     * @param apiKey
     *     api token
     */
    public NovitaAiChatModel(String modelName, String apiKey) {
        super(modelName, apiKey);
    }

    /**
     * Builder access.
     *
     * @return
     *      builder instance
     */
    public static Builder builder() {
        for (NovitaAiChatModelBuilderFactory factory : loadFactories(NovitaAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    /**
     * Internal Builder.
     */
    public static class Builder {
        
        /**
         * ModelName, preferred as enum for extensibility.
         */
        public String apiKey;
        /**
         * ModelName, preferred as enum for extensibility.
         */
        public NovitaAiChatModelName modelName;

        /**
         * Simple constructor.
         */
        public Builder() {
        }

        /**
         * Sets the apiKey for the Novita AI model builder.
         *
         * @param apiKey The apiKey to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the model name for the Novita AI model builder.
         *
         * @param modelName The name of the model to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder modelName(NovitaAiChatModelName modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Builds a new instance of Novita AI Chat Model.
         *
         * @return A new instance of {@link NovitaAiChatModel}.
         */
        public NovitaAiChatModel build() {
            return new NovitaAiChatModel(this);
        }
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestValidator.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidator.validateParameters(parameters);
        ChatRequestValidator.validate(parameters.toolSpecifications());
        ChatRequestValidator.validate(parameters.toolChoice());
        ChatRequestValidator.validate(parameters.responseFormat());

        Response<AiMessage> response = generate(chatRequest.messages());

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    private Response<AiMessage> generate(List<ChatMessage> messages) {
        NovitaAiChatCompletionRequest req = new NovitaAiChatCompletionRequest();
        req.setMessages(messages.stream()
                .map(this::toMessage)
                .collect(Collectors.toList()));
        req.setModel(modelName);
        NovitaAiChatCompletionResponse response = generate(req);
        return Response.from(
                aiMessageFrom(response),
                tokenUsageFrom(response.getUsage()),
                finishReasonFrom(response.getChoices().get(0).getFinishReason()));
    }

    /**
     * Mapping ChatMessage to ChatTextGenerationRequest.Message
     *
     * @param message
     *      inbound message
     * @return
     *      message for request
     */
    private NovitaAiChatCompletionRequest.Message toMessage(ChatMessage message) {
        return new NovitaAiChatCompletionRequest.Message(
                NovitaAiChatCompletionRequest.MessageRole.valueOf(message.type().name().toLowerCase()),
                message.text());
    }


    public NovitaAiChatCompletionResponse generate(NovitaAiChatCompletionRequest request) {
        try {
            retrofit2.Response<dev.langchain4j.model.novitaai.client.NovitaAiChatCompletionResponse> retrofitResponse = novitaClient
                    .generateChat(request)
                    .execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
