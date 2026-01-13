package dev.langchain4j.model.localai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.localai.spi.LocalAiChatModelBuilderFactory;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.aiMessageFrom;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.finishReasonFrom;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.toFunctions;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.toOpenAiMessages;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

/**
 * See <a href="https://localai.io/features/text-generation/">LocalAI documentation</a> for more details.
 */
public class LocalAiChatModel implements ChatModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Integer maxRetries;

    @Deprecated(forRemoval = true, since = "1.5.0")
    public LocalAiChatModel(String baseUrl,
                            String modelName,
                            Double temperature,
                            Double topP,
                            Integer maxTokens,
                            Duration timeout,
                            Integer maxRetries,
                            Boolean logRequests,
                            Boolean logResponses) {

        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(60) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        this.client = OpenAiClient.builder()
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.maxRetries = maxRetries;
    }

    public LocalAiChatModel(LocalAiChatModelBuilder builder) {
        this.client = OpenAiClient.builder()
                .baseUrl(ensureNotBlank(builder.baseUrl, "baseUrl"))
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .readTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logger(builder.logger)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.temperature = getOrDefault(builder.temperature, 0.7);
        this.topP = builder.topP;
        this.maxTokens = builder.maxTokens;
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        Response<AiMessage> response;
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            response = generate(chatRequest.messages());
        } else {
            if (parameters.toolChoice() == REQUIRED) {
                if (toolSpecifications.size() != 1) {
                    throw new UnsupportedFeatureException(
                            String.format("%s.%s is currently supported only when there is a single tool",
                                    ToolChoice.class.getSimpleName(), REQUIRED.name()));
                }
                response = generate(chatRequest.messages(), toolSpecifications.get(0));
            } else {
                response = generate(chatRequest.messages(), toolSpecifications);
            }
        }

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    private Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, null, null);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, toolSpecifications, null);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, singletonList(toolSpecification), toolSpecification);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages,
                                         List<ToolSpecification> toolSpecifications,
                                         ToolSpecification toolThatMustBeExecuted
    ) {
        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens);

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            requestBuilder.functions(toFunctions(toolSpecifications));
        }
        if (toolThatMustBeExecuted != null) {
            requestBuilder.functionCall(toolThatMustBeExecuted.name());
        }

        ChatCompletionRequest request = requestBuilder.build();

        ChatCompletionResponse response = withRetryMappingExceptions(() -> client.chatCompletion(request).execute(), maxRetries);

        return Response.from(
                aiMessageFrom(response),
                null,
                finishReasonFrom(response.choices().get(0).finishReason())
        );
    }

    public static LocalAiChatModelBuilder builder() {
        for (LocalAiChatModelBuilderFactory factory : loadFactories(LocalAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new LocalAiChatModelBuilder();
    }

    public static class LocalAiChatModelBuilder {
        private String baseUrl;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        public LocalAiChatModelBuilder() {
            // This is public so it can be extended
        }

        public LocalAiChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public LocalAiChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public LocalAiChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public LocalAiChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public LocalAiChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public LocalAiChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public LocalAiChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public LocalAiChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public LocalAiChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public LocalAiChatModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public LocalAiChatModel build() {
            return new LocalAiChatModel(this);
        }

        public String toString() {
            return "LocalAiChatModel.LocalAiChatModelBuilder(baseUrl=" + this.baseUrl + ", modelName=" + this.modelName + ", temperature=" + this.temperature + ", topP=" + this.topP + ", maxTokens=" + this.maxTokens + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
