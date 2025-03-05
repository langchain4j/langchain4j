package dev.langchain4j.model.anthropic;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolChoice;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createModelListenerRequest;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.EPHEMERAL;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.NO_CACHE;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicSystemPrompt;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicTools;
import static dev.langchain4j.model.anthropic.internal.sanitizer.MessageSanitizer.sanitizeMessages;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Represents an Anthropic language model with a Messages (chat) API.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <br>
 * More details are available <a href="https://docs.anthropic.com/claude/reference/messages_post">here</a>
 * and <a href="https://docs.anthropic.com/claude/reference/messages-streaming">here</a>.
 * <br>
 * <br>
 * It supports {@link Image}s as inputs. {@link UserMessage}s can contain one or multiple {@link ImageContent}s.
 * {@link Image}s must not be represented as URLs; they should be Base64-encoded strings and include a {@code mimeType}.
 * <br>
 * <br>
 * The content of {@link SystemMessage}s is sent using the "system" parameter.
 * <br>
 * <br>
 * Sanitization is performed on the {@link ChatMessage}s provided to ensure conformity with Anthropic API requirements.
 * This includes ensuring the first message is a {@link UserMessage} and that there are no consecutive {@link UserMessage}s.
 * Any messages removed during sanitization are logged as warnings and not submitted to the API.
 * <br>
 * <br>
 * Supports caching {@link SystemMessage}s and {@link ToolSpecification}s.
 */
@Slf4j
public class AnthropicStreamingChatModel implements StreamingChatLanguageModel {

    private final AnthropicClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final int maxTokens;
    private final List<String> stopSequences;
    private final boolean cacheSystemMessages;
    private final boolean cacheTools;
    private final List<ChatModelListener> listeners;

    /**
     * Constructs an instance of an {@code AnthropicStreamingChatModel} with the specified parameters.
     *
     * @param baseUrl             The base URL of the Anthropic API. Default: "https://api.anthropic.com/v1/"
     * @param apiKey              The API key for authentication with the Anthropic API.
     * @param version             The value of the "anthropic-version" HTTP header. Default: "2023-06-01"
     * @param beta                The value of the "anthropic-beta" HTTP header.
     * @param modelName           The name of the Anthropic model to use.
     * @param temperature         The temperature
     * @param topP                The top-P
     * @param topK                The top-K
     * @param maxTokens           The maximum number of tokens to generate. Default: 1024
     * @param stopSequences       The custom text sequences that will cause the model to stop generating
     * @param cacheSystemMessages If true, it will add cache_control block to all system messages. Default: false
     * @param cacheTools          If true, it will add cache_control block to all tools. Default: false
     * @param timeout             The timeout for API requests. Default: 60 seconds
     * @param logRequests         Whether to log the content of API requests using SLF4J. Default: false
     * @param logResponses        Whether to log the content of API responses using SLF4J. Default: false
     * @param listeners           A list of {@link ChatModelListener} instances to be notified.
     */
    @Builder
    private AnthropicStreamingChatModel(String baseUrl,
                                        String apiKey,
                                        String version,
                                        String beta,
                                        String modelName,
                                        Double temperature,
                                        Double topP,
                                        Integer topK,
                                        Integer maxTokens,
                                        List<String> stopSequences,
                                        Boolean cacheSystemMessages,
                                        Boolean cacheTools,
                                        Duration timeout,
                                        Boolean logRequests,
                                        Boolean logResponses,
                                        List<ChatModelListener> listeners) {
        this.client = AnthropicClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://api.anthropic.com/v1/"))
                .apiKey(apiKey)
                .version(getOrDefault(version, "2023-06-01"))
                .beta(beta)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.maxTokens = getOrDefault(maxTokens, 1024);
        this.stopSequences = stopSequences;
        this.cacheSystemMessages = getOrDefault(cacheSystemMessages, false);
        this.cacheTools = getOrDefault(cacheTools, false);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    public static class AnthropicStreamingChatModelBuilder {

        public AnthropicStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public AnthropicStreamingChatModelBuilder modelName(AnthropicChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidator.validateParameters(parameters);
        ChatRequestValidator.validate(parameters.responseFormat());

        StreamingResponseHandler<AiMessage> legacyHandler = new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                handler.onPartialResponse(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                ChatResponse chatResponse = ChatResponse.builder()
                        .aiMessage(response.content())
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(response.tokenUsage())
                                .finishReason(response.finishReason())
                                .build())
                        .build();
                handler.onCompleteResponse(chatResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };

        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            generate(chatRequest.messages(), legacyHandler);
        } else {
            if (parameters.toolChoice() == REQUIRED) {
                if (toolSpecifications.size() != 1) {
                    throw new UnsupportedFeatureException(
                            String.format("%s.%s is currently supported only when there is a single tool",
                                    ToolChoice.class.getSimpleName(), REQUIRED.name()));
                }
                generate(chatRequest.messages(), toolSpecifications.get(0), legacyHandler);
            } else {
                generate(chatRequest.messages(), toolSpecifications, legacyHandler);
            }
        }
    }

    private void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, null, handler);
    }

    private void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, toolSpecifications, null, handler);
    }

    private void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, toolSpecification, handler);
    }

    private void generate(List<ChatMessage> messages,
                          List<ToolSpecification> toolSpecifications,
                          ToolSpecification toolThatMustBeExecuted,
                          StreamingResponseHandler<AiMessage> handler) {

        List<ChatMessage> sanitizedMessages = sanitizeMessages(messages);
        List<AnthropicTextContent> systemPrompt = toAnthropicSystemPrompt(messages, cacheSystemMessages ? EPHEMERAL : NO_CACHE);
        ensureNotNull(handler, "handler");

        AnthropicCreateMessageRequest.AnthropicCreateMessageRequestBuilder requestBuilder = AnthropicCreateMessageRequest.builder()
                .stream(true)
                .model(modelName)
                .messages(toAnthropicMessages(sanitizedMessages))
                .system(systemPrompt)
                .maxTokens(maxTokens)
                .stopSequences(stopSequences)
                .temperature(temperature)
                .topP(topP)
                .topK(topK);

        AnthropicCacheType toolsCacheType = cacheTools ? EPHEMERAL : NO_CACHE;
        if (toolThatMustBeExecuted != null) {
            requestBuilder.tools(toAnthropicTools(singletonList(toolThatMustBeExecuted), toolsCacheType));
            requestBuilder.toolChoice(AnthropicToolChoice.from(toolThatMustBeExecuted.name()));
        } else if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toAnthropicTools(toolSpecifications, toolsCacheType));
        }

        AnthropicCreateMessageRequest request = requestBuilder.build();

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

        StreamingResponseHandler<AiMessage> listenerHandler = new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
                handler.onNext(token);
            }

            @Override
            public void onError(Throwable error) {
                ChatModelErrorContext errorContext = InternalAnthropicHelper.createErrorContext(
                        error,
                        modelListenerRequest,
                        attributes
                );

                listeners.forEach(listener -> {
                    try {
                        listener.onError(errorContext);
                    } catch (Exception e2) {
                        log.warn("Exception while calling model listener", e2);
                    }
                });

                handler.onError(error);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                ChatModelResponse modelListenerResponse = InternalAnthropicHelper.createModelListenerResponse(
                        (String) response.metadata().get("id"),
                        (String) response.metadata().get("model"),
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
                StreamingResponseHandler.super.onComplete(response);
            }
        };

        client.createMessage(request, listenerHandler);
    }
}
