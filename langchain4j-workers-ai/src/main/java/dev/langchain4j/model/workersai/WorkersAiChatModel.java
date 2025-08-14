package dev.langchain4j.model.workersai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.workersai.client.AbstractWorkersAIModel;
import dev.langchain4j.model.workersai.client.WorkersAiChatCompletionRequest;
import dev.langchain4j.model.workersai.spi.WorkersAiChatModelBuilderFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * WorkerAI Chat model.
 * <a href="https://developers.cloudflare.com/api/operations/workers-ai-post-run-model">...</a>
 */
public class WorkersAiChatModel extends AbstractWorkersAIModel implements ChatModel {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(WorkersAiChatModel.class);

    /**
     * Constructor with Builder.
     *
     * @param builder builder.
     */
    public WorkersAiChatModel(Builder builder) {
        this(builder.accountId, builder.modelName, builder.apiToken);
    }

    /**
     * Constructor with Builder.
     *
     * @param accountId account identifier
     * @param modelName model name
     * @param apiToken  api token
     */
    public WorkersAiChatModel(String accountId, String modelName, String apiToken) {
        super(accountId, modelName, apiToken);
    }

    /**
     * Builder access.
     *
     * @return builder instance
     */
    public static Builder builder() {
        for (WorkersAiChatModelBuilderFactory factory : loadFactories(WorkersAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    /**
     * Internal Builder.
     */
    public static class Builder {

        /**
         * Account identifier, provided by the WorkerAI platform.
         */
        public String accountId;
        /**
         * ModelName, preferred as enum for extensibility.
         */
        public String apiToken;
        /**
         * ModelName, preferred as enum for extensibility.
         */
        public String modelName;

        /**
         * Simple constructor.
         */
        public Builder() {
        }

        /**
         * Simple constructor.
         *
         * @param accountId account identifier.
         * @return self reference
         */
        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        /**
         * Sets the apiToken for the Worker AI model builder.
         *
         * @param apiToken The apiToken to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
            return this;
        }

        /**
         * Sets the model name for the Worker AI model builder.
         *
         * @param modelName The name of the model to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Builds a new instance of Worker AI Chat Model.
         *
         * @return A new instance of {@link WorkersAiChatModel}.
         */
        public WorkersAiChatModel build() {
            return new WorkersAiChatModel(this);
        }
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolSpecifications());
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

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
        WorkersAiChatCompletionRequest req = new WorkersAiChatCompletionRequest();
        req.setMessages(messages.stream()
                .map(this::toMessage)
                .collect(Collectors.toList()));
        return new Response<>(new AiMessage(generate(req)), null, FinishReason.STOP);
    }

    /**
     * Mapping ChatMessage to ChatTextGenerationRequest.Message
     *
     * @param message inbound message
     * @return message for request
     */
    private WorkersAiChatCompletionRequest.Message toMessage(ChatMessage message) {
        return new WorkersAiChatCompletionRequest.Message(
                WorkersAiChatCompletionRequest.MessageRole.valueOf(message.type().name().toLowerCase()),
                toText(message)
        );
    }

    private static String toText(ChatMessage chatMessage) {
        if (chatMessage instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        } else if (chatMessage instanceof UserMessage userMessage) {
            return userMessage.singleText();
        } else if (chatMessage instanceof AiMessage aiMessage) {
            return aiMessage.text();
        } else if (chatMessage instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return toolExecutionResultMessage.text();
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + chatMessage.type());
        }
    }

    /**
     * Invoke endpoint and process error.
     *
     * @param req request
     * @return text generated by the model
     */
    private String generate(WorkersAiChatCompletionRequest req) {
        try {
            retrofit2.Response<dev.langchain4j.model.workersai.client.WorkersAiChatCompletionResponse> retrofitResponse = workerAiClient
                    .generateChat(req, accountId, modelName)
                    .execute();
            processErrors(retrofitResponse.body(), retrofitResponse.errorBody());
            if (retrofitResponse.body() == null) {
                throw new IllegalStateException("Response is empty");
            }
            return retrofitResponse.body().getResult().getResponse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
