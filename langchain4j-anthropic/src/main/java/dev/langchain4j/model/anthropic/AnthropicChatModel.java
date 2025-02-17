package dev.langchain4j.model.anthropic;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_HAIKU_20240307;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createErrorContext;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createModelListenerRequest;
import static dev.langchain4j.model.anthropic.InternalAnthropicHelper.createModelListenerResponse;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.EPHEMERAL;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType.NO_CACHE;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.*;
import static dev.langchain4j.model.anthropic.internal.sanitizer.MessageSanitizer.sanitizeMessages;
import static java.util.Collections.emptyList;

/**
 * Represents an Anthropic language model with a Messages (chat) API.
 * <br>
 * More details are available <a href="https://docs.anthropic.com/claude/reference/messages_post">here</a>.
 * <br>
 * <br>
 * It supports tools. See more information <a href="https://docs.anthropic.com/claude/docs/tool-use">here</a>.
 * <br>
 * <br>
 * It supports {@link Image}s as inputs. {@link UserMessage}s can contain one or multiple {@link ImageContent}s.
 * {@link Image}s must not be represented as URLs; they should be Base64-encoded strings and include a {@code mimeType}.
 * <br>
 * <br>
 * The content of {@link SystemMessage}s is sent using the "system" parameter.
 * <br>
 * <br>
 * Sanitization is performed on the {@link ChatMessage}s provided to conform to Anthropic API requirements. This process
 * includes verifying that the first message is a {@link UserMessage} and removing any consecutive {@link UserMessage}s.
 * Any messages removed during sanitization are logged as warnings and not submitted to the API.
 * <br>
 * <br>
 * Supports caching {@link SystemMessage}s and {@link ToolSpecification}s.
 */
@Slf4j
public class AnthropicChatModel implements ChatLanguageModel {

    private final AnthropicClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
    private final int maxTokens;
    private final List<String> stopSequences;
    private final boolean cacheSystemMessages;
    private final boolean cacheTools;
    private final int maxRetries;
    private final List<ChatModelListener> listeners;

    /**
     * Constructs an instance of an {@code AnthropicChatModel} with the specified parameters.
     *
     * @param baseUrl             The base URL of the Anthropic API. Default: "https://api.anthropic.com/v1/"
     * @param apiKey              The API key for authentication with the Anthropic API.
     * @param version             The value of the "anthropic-version" HTTP header. Default: "2023-06-01"
     * @param beta                The value of the "anthropic-beta" HTTP header.
     * @param modelName           The name of the Anthropic model to use. Default: "claude-3-haiku-20240307"
     * @param temperature         The temperature
     * @param topP                The top-P
     * @param topK                The top-K
     * @param maxTokens           The maximum number of tokens to generate. Default: 1024
     * @param stopSequences       The custom text sequences that will cause the model to stop generating
     * @param cacheSystemMessages If true, it will add cache_control block to all system messages. Default: false
     * @param cacheTools          If true, it will add cache_control block to all tools. Default: false
     * @param timeout             The timeout for API requests. Default: 60 seconds
     * @param maxRetries          The maximum number of retries for API requests. Default: 3
     * @param logRequests         Whether to log the content of API requests using SLF4J. Default: false
     * @param logResponses        Whether to log the content of API responses using SLF4J. Default: false
     * @param listeners           A list of {@link ChatModelListener} instances to be notified.
     */
    @Builder
    private AnthropicChatModel(String baseUrl,
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
                               Integer maxRetries,
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
        this.modelName = getOrDefault(modelName, CLAUDE_3_HAIKU_20240307.toString());
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.maxTokens = getOrDefault(maxTokens, 1024);
        this.stopSequences = stopSequences;
        this.cacheSystemMessages = getOrDefault(cacheSystemMessages, false);
        this.cacheTools = getOrDefault(cacheTools, false);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
    }

    public static class AnthropicChatModelBuilder {

        public AnthropicChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public AnthropicChatModelBuilder modelName(AnthropicChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }

    /**
     * @deprecated Please use {@code builder()} instead, and explicitly set the model name and,
     * if necessary, other parameters.
     * <b>The default value for the model name will be removed in future releases!</b>
     */
    @Deprecated(forRemoval = true)
    public static AnthropicChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, (List<ToolSpecification>) null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {

        List<ChatMessage> sanitizedMessages = sanitizeMessages(messages);
        List<AnthropicTextContent> systemPrompt = toAnthropicSystemPrompt(messages, cacheSystemMessages ? EPHEMERAL : NO_CACHE);

        AnthropicCreateMessageRequest request = AnthropicCreateMessageRequest.builder()
                .model(modelName)
                .messages(toAnthropicMessages(sanitizedMessages))
                .system(systemPrompt)
                .maxTokens(maxTokens)
                .stopSequences(stopSequences)
                .stream(false)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .tools(toAnthropicTools(toolSpecifications, cacheTools ? EPHEMERAL : NO_CACHE))
                .build();

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

        try {
            AnthropicCreateMessageResponse response = withRetry(() -> client.createMessage(request), maxRetries);
            Response<AiMessage> responseMessage = Response.from(
                    toAiMessage(response.content),
                    toTokenUsage(response.usage),
                    toFinishReason(response.stopReason)
            );

            ChatModelResponse modelListenerResponse = createModelListenerResponse(
                    response.id,
                    response.model,
                    responseMessage
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

            return Response.from(
                    toAiMessage(response.content),
                    toTokenUsage(response.usage),
                    toFinishReason(response.stopReason)
            );
        } catch (RuntimeException e) {
            ChatModelErrorContext errorContext = createErrorContext(
                    e,
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

            throw e;
        }
    }
    // TODO forcing tool use?
}
