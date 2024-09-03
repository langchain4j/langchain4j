package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionChoice;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.Delta;
import dev.ai4j.openai4j.chat.ResponseFormat;
import dev.ai4j.openai4j.chat.ResponseFormatType;
import dev.ai4j.openai4j.chat.StreamOptions;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.openai.spi.OpenAiStreamingChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_URL;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.createModelListenerRequest;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.createModelListenerResponse;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.isOpenAiModel;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.removeTokenUsage;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toTools;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Represents an OpenAI language model with a chat completion interface, such as gpt-3.5-turbo and gpt-4.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * You can find description of parameters <a href="https://platform.openai.com/docs/api-reference/chat/create">here</a>.
 */
@Slf4j
public class OpenAiStreamingChatModel implements StreamingChatLanguageModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final List<String> stop;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Map<String, Integer> logitBias;
    private final ResponseFormat responseFormat;
    private final Integer seed;
    private final String user;
    private final Boolean strictTools;
    private final Boolean parallelToolCalls;
    private final Tokenizer tokenizer;
    private final boolean isOpenAiModel;
    private final List<ChatModelListener> listeners;

    @Builder
    public OpenAiStreamingChatModel(String baseUrl,
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
                                    Boolean strictTools,
                                    Boolean parallelToolCalls,
                                    Duration timeout,
                                    Proxy proxy,
                                    Boolean logRequests,
                                    Boolean logResponses,
                                    Tokenizer tokenizer,
                                    Map<String, String> customHeaders,
                                    List<ChatModelListener> listeners) {

        timeout = getOrDefault(timeout, ofSeconds(60));

        this.client = OpenAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, OPENAI_URL))
                .openAiApiKey(apiKey)
                .organizationId(organizationId)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(customHeaders)
                .build();
        this.modelName = getOrDefault(modelName, GPT_3_5_TURBO);
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.stop = stop;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.logitBias = logitBias;
        this.responseFormat = responseFormat == null ? null : ResponseFormat.builder()
                .type(ResponseFormatType.valueOf(responseFormat.toUpperCase(Locale.ROOT)))
                .build();
        this.seed = seed;
        this.user = user;
        this.strictTools = getOrDefault(strictTools, false);
        this.parallelToolCalls = parallelToolCalls;
        this.tokenizer = getOrDefault(tokenizer, OpenAiTokenizer::new);
        this.isOpenAiModel = isOpenAiModel(this.modelName);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    public String modelName() {
        return modelName;
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
                .streamOptions(StreamOptions.builder()
                        .includeUsage(true)
                        .build())
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
                .user(user)
                .parallelToolCalls(parallelToolCalls);

        if (toolThatMustBeExecuted != null) {
            requestBuilder.tools(toTools(singletonList(toolThatMustBeExecuted), strictTools));
            requestBuilder.toolChoice(toolThatMustBeExecuted.name());
        } else if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toTools(toolSpecifications, strictTools));
        }

        ChatCompletionRequest request = requestBuilder.build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(request, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        int inputTokenCount = countInputTokens(messages, toolSpecifications, toolThatMustBeExecuted);
        OpenAiStreamingResponseBuilder responseBuilder = new OpenAiStreamingResponseBuilder(inputTokenCount);

        AtomicReference<String> responseId = new AtomicReference<>();
        AtomicReference<String> responseModel = new AtomicReference<>();

        client.chatCompletion(request)
                .onPartialResponse(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    handle(partialResponse, handler);

                    if (!isNullOrBlank(partialResponse.id())) {
                        responseId.set(partialResponse.id());
                    }
                    if (!isNullOrBlank(partialResponse.model())) {
                        responseModel.set(partialResponse.model());
                    }
                })
                .onComplete(() -> {
                    Response<AiMessage> response = createResponse(responseBuilder, toolThatMustBeExecuted);

                    ChatModelResponse modelListenerResponse = createModelListenerResponse(
                            responseId.get(),
                            responseModel.get(),
                            response
                    );
                    ChatModelResponseContext responseContext = new ChatModelResponseContext(
                            modelListenerResponse,
                            modelListenerRequest,
                            attributes
                    );
                    listeners.forEach(listener -> {
                        try {
                            listener.onResponse(responseContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener", e);
                        }
                    });

                    handler.onComplete(response);
                })
                .onError(error -> {
                    Response<AiMessage> response = createResponse(responseBuilder, toolThatMustBeExecuted);

                    ChatModelResponse modelListenerPartialResponse = createModelListenerResponse(
                            responseId.get(),
                            responseModel.get(),
                            response
                    );

                    ChatModelErrorContext errorContext = new ChatModelErrorContext(
                            error,
                            modelListenerRequest,
                            modelListenerPartialResponse,
                            attributes
                    );

                    listeners.forEach(listener -> {
                        try {
                            listener.onError(errorContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener", e);
                        }
                    });

                    handler.onError(error);
                })
                .execute();
    }

    private Response<AiMessage> createResponse(OpenAiStreamingResponseBuilder responseBuilder,
                                               ToolSpecification toolThatMustBeExecuted) {
        Response<AiMessage> response = responseBuilder.build(tokenizer, toolThatMustBeExecuted != null);
        if (isOpenAiModel) {
            return response;
        }
        return removeTokenUsage(response);
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

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        return tokenizer.estimateTokenCountInMessages(messages);
    }

    public static OpenAiStreamingChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static OpenAiStreamingChatModelBuilder builder() {
        for (OpenAiStreamingChatModelBuilderFactory factory : loadFactories(OpenAiStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiStreamingChatModelBuilder();
    }

    public static class OpenAiStreamingChatModelBuilder {

        public OpenAiStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public OpenAiStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiStreamingChatModelBuilder modelName(OpenAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }
}
