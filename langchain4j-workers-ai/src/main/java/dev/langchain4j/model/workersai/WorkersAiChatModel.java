package dev.langchain4j.model.workersai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.workersai.client.AbstractWorkersAIModel;
import dev.langchain4j.model.workersai.client.WorkersAiChatCompletionRequest;
import dev.langchain4j.model.workersai.spi.WorkersAiChatModelBuilderFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * WorkerAI Chat model.
 * <a href="https://developers.cloudflare.com/api/operations/workers-ai-post-run-model">...</a>
 */
@Slf4j
public class WorkersAiChatModel extends AbstractWorkersAIModel implements ChatLanguageModel {

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      builder.
     */
    public WorkersAiChatModel(Builder builder) {
       this(builder.accountId, builder.modelName, builder.apiToken);
    }

    /**
     * Constructor with Builder.
     *
     * @param accountId
     *      account identifier
     * @param modelName
     *      model name
     * @param apiToken
     *     api token
     */
    public WorkersAiChatModel(String accountId, String modelName, String apiToken) {
        super(accountId, modelName, apiToken);
    }

    /**
     * Builder access.
     *
     * @return
     *      builder instance
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
         * @param accountId
         *      account identifier.
         * @return
         *      self reference
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

    /** {@inheritDoc} */
    @Override
    public String generate(String userMessage) {
        return generate(new WorkersAiChatCompletionRequest(WorkersAiChatCompletionRequest.MessageRole.user, userMessage));
    }

    /** {@inheritDoc} */
    @Override
    public Response<AiMessage> generate(@NonNull  ChatMessage... messages) {
        return generate(Arrays.asList(messages));
    }

    /** {@inheritDoc} */
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        WorkersAiChatCompletionRequest req = new WorkersAiChatCompletionRequest();
        req.setMessages(messages.stream()
                .map(this::toMessage)
                .collect(Collectors.toList()));
        return new Response<>(new AiMessage(generate(req)),null, FinishReason.STOP);
    }

    /** {@inheritDoc} */
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new UnsupportedOperationException("Tools are currently not supported for WorkerAI models");
    }

    /** {@inheritDoc} */
    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        throw new UnsupportedOperationException("Tools are currently not supported for WorkerAI models");
    }

    /**
     * Mapping ChatMessage to ChatTextGenerationRequest.Message
     *
     * @param message
     *      inbound message
     * @return
     *      message for request
     */
    private WorkersAiChatCompletionRequest.Message toMessage(ChatMessage message) {
        return new WorkersAiChatCompletionRequest.Message(
               WorkersAiChatCompletionRequest.MessageRole.valueOf(message.type().name().toLowerCase()),
                message.text());
    }

    /**
     * Invoke endpoint and process error.
     *
     * @param req
     *      request
     * @return
     *      text generated by the model
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