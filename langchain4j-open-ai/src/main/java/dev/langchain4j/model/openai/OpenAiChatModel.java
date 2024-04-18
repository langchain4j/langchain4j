package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionChoice;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.Delta;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.openai.spi.OpenAiChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.*;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

/**
 * Represents an OpenAI language model with a chat completion interface, such as gpt-3.5-turbo and gpt-4.
 * You can find description of parameters <a href="https://platform.openai.com/docs/api-reference/chat/create">here</a>.
 */
public class OpenAiChatModel implements ChatLanguageModel, StreamingChatLanguageModel, TokenCountEstimator {

    protected final OpenAiClient client;
    protected final String modelName;
    protected final Double temperature;
    protected final Double topP;
    protected final List<String> stop;
    protected final Integer maxTokens;
    protected final Double presencePenalty;
    protected final Double frequencyPenalty;
    protected final Map<String, Integer> logitBias;
    protected final String responseFormat;
    protected final Integer seed;
    protected final String user;
    protected final Integer maxRetries;
    protected final Tokenizer tokenizer;
    protected final boolean isOpenAiModel;

    @Builder
    public OpenAiChatModel(String baseUrl,
                           String apiKey,
                           String organizationId,
                           String modelName,
                           Double temperature,
                           Double topP,
                           List<String> stop,
                           Integer maxTokens,
                           Double presencePenalty,
                           Double frequencyPenalty,
                           Map<String, Integer> logitBias,
                           String responseFormat,
                           Integer seed,
                           String user,
                           Duration timeout,
                           Integer maxRetries,
                           Proxy proxy,
                           Boolean logRequests,
                           Boolean logResponses,
                           Tokenizer tokenizer,
                           Map<String, String> customHeaders,
                           boolean isStreaming) {

        baseUrl = getOrDefault(baseUrl, OPENAI_URL);
        if (OPENAI_DEMO_API_KEY.equals(apiKey)) {
            baseUrl = OPENAI_DEMO_URL;
        }

        timeout = getOrDefault(timeout, ofSeconds(60));

        OpenAiClient.Builder openAiClientBuilder = OpenAiClient.builder()
                .openAiApiKey(apiKey)
                .baseUrl(baseUrl)
                .organizationId(organizationId)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(customHeaders);

        if (isStreaming) {
            openAiClientBuilder.logResponses(logResponses);
        } else {
            openAiClientBuilder.logStreamingResponses(logResponses);
        }
        this.client = openAiClientBuilder.build();

        this.modelName = getOrDefault(modelName, GPT_3_5_TURBO);
        this.isOpenAiModel = isOpenAiModel(modelName);

        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.stop = stop;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.logitBias = logitBias;
        this.responseFormat = responseFormat;
        this.seed = seed;
        this.user = user;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.tokenizer = getOrDefault(tokenizer, OpenAiTokenizer::new);
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generateStreaming(messages, null, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generateStreaming(messages, toolSpecifications, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generateStreaming(messages, singletonList(toolSpecification), toolSpecification);
    }

    private Response<AiMessage> generateStreaming(List<ChatMessage> messages,
                                         List<ToolSpecification> toolSpecifications,
                                         ToolSpecification toolThatMustBeExecuted
    ) {
        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .stop(stop)
                .maxTokens(maxTokens)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
                .logitBias(logitBias)
                .responseFormat(responseFormat)
                .seed(seed)
                .user(user);

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            requestBuilder.tools(toTools(toolSpecifications));
        }
        if (toolThatMustBeExecuted != null) {
            requestBuilder.toolChoice(toolThatMustBeExecuted.name());
        }

        ChatCompletionRequest request = requestBuilder.build();

        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(request).execute(), maxRetries);

        return Response.from(
                aiMessageFrom(response),
                tokenUsageFrom(response.usage()),
                finishReasonFrom(response.choices().get(0).finishReason())
        );
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        return tokenizer.estimateTokenCountInMessages(messages);
    }

    public static OpenAiChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static OpenAiChatModelBuilder builder() {
        for (OpenAiChatModelBuilderFactory factory : loadFactories(OpenAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiChatModelBuilder();
    }

    public static class OpenAiChatModelBuilder {

        public OpenAiChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public OpenAiChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiChatModelBuilder modelName(OpenAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, toolSpecifications, null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, toolSpecification, handler);
    }

    private void generate(List<ChatMessage> messages,
                          List<ToolSpecification> toolSpecifications,
                          ToolSpecification toolThatMustBeExecuted,
                          StreamingResponseHandler<AiMessage> handler
    ) {
        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .stream(true)
                .model(modelName)
                .messages(toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .stop(stop)
                .maxTokens(maxTokens)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
                .logitBias(logitBias)
                .responseFormat(responseFormat)
                .seed(seed)
                .user(user);

        if (toolThatMustBeExecuted != null) {
            requestBuilder.tools(toTools(singletonList(toolThatMustBeExecuted)));
            requestBuilder.toolChoice(toolThatMustBeExecuted.name());
        } else if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toTools(toolSpecifications));
        }

        ChatCompletionRequest request = requestBuilder.build();

        int inputTokenCount = countInputTokens(messages, toolSpecifications, toolThatMustBeExecuted);
        OpenAiStreamingResponseBuilder responseBuilder = new OpenAiStreamingResponseBuilder(inputTokenCount);

        client.chatCompletion(request)
                .onPartialResponse(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    handle(partialResponse, handler);
                })
                .onComplete(() -> {
                    Response<AiMessage> response = responseBuilder.build(tokenizer, toolThatMustBeExecuted != null);
                    if (!isOpenAiModel) {
                        response = removeTokenUsage(response);
                    }
                    handler.onComplete(response);
                })
                .onError(handler::onError)
                .execute();
    }

    private int countInputTokens(List<ChatMessage> messages,
                                 List<ToolSpecification> toolSpecifications,
                                 ToolSpecification toolThatMustBeExecuted) {
        int inputTokenCount = tokenizer.estimateTokenCountInMessages(messages);
        if (toolThatMustBeExecuted != null) {
            inputTokenCount += tokenizer.estimateTokenCountInForcefulToolSpecification(toolThatMustBeExecuted);
        } else if (!isNullOrEmpty(toolSpecifications)) {
            inputTokenCount += tokenizer.estimateTokenCountInToolSpecifications(toolSpecifications);
        }
        return inputTokenCount;
    }

    private static void handle(ChatCompletionResponse partialResponse,
                               StreamingResponseHandler<AiMessage> handler) {
        List<ChatCompletionChoice> choices = partialResponse.choices();
        if (choices == null || choices.isEmpty()) {
            return;
        }
        Delta delta = choices.get(0).delta();
        String content = delta.content();
        if (content != null) {
            handler.onNext(content);
        }
    }
}
