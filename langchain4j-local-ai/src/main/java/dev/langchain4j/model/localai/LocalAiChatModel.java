package dev.langchain4j.model.localai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.localai.spi.LocalAiChatModelBuilderFactory;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.aiMessageFrom;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.finishReasonFrom;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toFunctions;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

/**
 * See <a href="https://localai.io/features/text-generation/">LocalAI documentation</a> for more details.
 */
public class LocalAiChatModel implements ChatLanguageModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Integer maxRetries;

    @Builder
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

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidator.validateParameters(parameters);
        ChatRequestValidator.validate(parameters.responseFormat());

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

        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(request).execute(), maxRetries);

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
        public LocalAiChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
