package dev.langchain4j.model.anthropic.internal.client;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.http.client.sse.ServerSentEventParsingHandleUtils.toStreamingHandle;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.internal.client.Json.fromJson;
import static dev.langchain4j.model.anthropic.internal.client.Json.toJson;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.REDACTED_THINKING_KEY;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.SERVER_TOOL_RESULTS_KEY;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.THINKING_SIGNATURE_KEY;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;
import static java.util.Collections.synchronizedList;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.CancellationUnsupportedHandle;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.anthropic.AnthropicChatResponseMetadata;
import dev.langchain4j.model.anthropic.AnthropicServerToolResult;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCountTokensRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicDelta;
import dev.langchain4j.model.anthropic.internal.api.AnthropicModelsListResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicResponseMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicStreamingData;
import dev.langchain4j.model.anthropic.internal.api.AnthropicUsage;
import dev.langchain4j.model.anthropic.internal.api.MessageTokenCountResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link AnthropicClient} that provides methods to interact with the
 * Anthropic API for creating messages, counting tokens, and listing models.
 *
 * <p>This client handles both synchronous and streaming responses, managing HTTP requests efficiently
 * and processing server-sent events (SSE) for streaming data. It supports detailed logging of requests
 * and responses for debugging purposes.</p>
 *
 * <h2>HTTP Client Configuration</h2>
 * <p>Uses {@link HttpClientBuilderLoader} for flexible HTTP client construction with configurable timeouts:</p>
 * <ul>
 *   <li>Connection timeout: defaults to 15 seconds</li>
 *   <li>Read timeout: defaults to 60 seconds</li>
 * </ul>
 * <p>Request/response logging can be enabled via {@link LoggingHttpClient} using builder flags.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DefaultAnthropicClient client = DefaultAnthropicClient.builder()
 *     .baseUrl("https://api.anthropic.com/v1")
 *     .apiKey("your-api-key")
 *     .version("2023-06-01")
 *     .timeout(Duration.ofSeconds(30))
 *     .logRequests(true)
 *     .logResponses(true)
 *     .build();
 *
 * // Synchronous message creation
 * AnthropicCreateMessageResponse response = client.createMessage(request);
 *
 * // Streaming message creation
 * client.createMessage(request, options, new StreamingChatResponseHandler() {
 *     @Override
 *     public void onPartialResponse(String partialResponse, StreamingHandle handle) {
 *         System.out.print(partialResponse);
 *     }
 *
 *     @Override
 *     public void onCompleteResponse(ChatResponse completeResponse) {
 *         System.out.println("\nComplete: " + completeResponse.aiMessage().text());
 *     }
 *
 *     @Override
 *     public void onError(Throwable error) {
 *         error.printStackTrace();
 *     }
 * });
 * }</pre>
 */
@Internal
public class DefaultAnthropicClient extends AnthropicClient {
    private static final String CONTENT_BLOCK_TEXT = "text";
    private static final String CONTENT_BLOCK_THINKING = "thinking";
    private static final String CONTENT_BLOCK_REDACTED_THINKING = "redacted_thinking";
    private static final String CONTENT_BLOCK_TOOL_USE = "tool_use";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String version;
    private final String beta;

    /**
     * Creates a new builder for constructing a {@link DefaultAnthropicClient} instance.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing {@link DefaultAnthropicClient} instances.
     */
    public static class Builder extends AnthropicClient.Builder<DefaultAnthropicClient, Builder> {

        /**
         * Builds a new {@link DefaultAnthropicClient} instance with the configured settings.
         *
         * @return a new {@link DefaultAnthropicClient} instance
         * @throws IllegalArgumentException if required parameters ({@code baseUrl}, {@code apiKey}, {@code version}) are blank
         */
        public DefaultAnthropicClient build() {
            return new DefaultAnthropicClient(this);
        }
    }

    /**
     * Constructs a new {@link DefaultAnthropicClient} using the provided builder configuration.
     *
     * <p>Initializes the HTTP client with configured timeouts (defaulting to 15s connect, 60s read)
     * and optionally wraps it with {@link LoggingHttpClient} if request/response logging is enabled.</p>
     *
     * @param builder the builder containing configuration parameters
     * @throws IllegalArgumentException if {@code baseUrl}, {@code apiKey}, or {@code version} are blank
     */
    DefaultAnthropicClient(Builder builder) {

        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(
                        getOrDefault(builder.timeout, httpClientBuilder.connectTimeout()), Duration.ofSeconds(15)))
                .readTimeout(getOrDefault(
                        getOrDefault(builder.timeout, httpClientBuilder.readTimeout()), Duration.ofSeconds(60)))
                .build();

        if (builder.logRequests != null && builder.logRequests
                || builder.logResponses != null && builder.logResponses) {
            this.httpClient =
                    new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses, builder.logger);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.version = ensureNotBlank(builder.version, "version");
        this.beta = builder.beta;
    }

    /**
     * Creates a message synchronously using the Anthropic API.
     *
     * <p>Sends a request to the {@code /messages} endpoint and blocks until the response is received.</p>
     *
     * @param request the message creation request containing the model, messages, and other parameters
     * @return the parsed response from the Anthropic API
     * @throws RuntimeException if the HTTP request fails or the response cannot be parsed
     * @see #createMessageWithRawResponse(AnthropicCreateMessageRequest)
     */
    @Override
    public AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request) {
        return createMessageWithRawResponse(request).parsedResponse();
    }

    /**
     * Creates a message synchronously and returns both the parsed response and the raw HTTP response.
     *
     * <p>Useful when access to raw HTTP response details (headers, status code) is needed alongside
     * the parsed API response.</p>
     *
     * @param request the message creation request containing the model, messages, and other parameters
     * @return a {@link ParsedAndRawResponse} containing both the parsed {@link AnthropicCreateMessageResponse}
     *         and the raw {@link SuccessfulHttpResponse}
     * @throws RuntimeException if the HTTP request fails or the response cannot be parsed
     */
    @Override
    public ParsedAndRawResponse createMessageWithRawResponse(AnthropicCreateMessageRequest request) {
        HttpRequest httpRequest = toHttpRequest(toJson(request), "messages");
        SuccessfulHttpResponse rawResponse = httpClient.execute(httpRequest);
        AnthropicCreateMessageResponse parsedResponse =
                fromJson(rawResponse.body(), AnthropicCreateMessageResponse.class);
        return new ParsedAndRawResponse(parsedResponse, rawResponse);
    }

    /**
     * Creates a message with streaming response handling.
     *
     * <p>Sends a request to the {@code /messages} endpoint and processes the response as a stream
     * of server-sent events (SSE). The handler receives callbacks for:</p>
     * <ul>
     *   <li>Partial text responses as they arrive</li>
     *   <li>Partial thinking outputs (if {@code options.returnThinking()} is true)</li>
     *   <li>Partial and complete tool calls</li>
     *   <li>The complete response when streaming finishes</li>
     *   <li>Errors if they occur</li>
     * </ul>
     *
     * Supported SSE Event Types
     * <ul>
     *   <li>{@code message_start}: Initial message metadata including usage and model info</li>
     *   <li>{@code content_block_start}: Start of a content block (text, thinking, tool_use, server tool results)</li>
     *   <li>{@code content_block_delta}: Incremental content updates</li>
     *   <li>{@code content_block_stop}: End of a content block</li>
     *   <li>{@code message_delta}: Message-level updates including stop reason and final usage</li>
     *   <li>{@code message_stop}: End of message, triggers complete response callback</li>
     *   <li>{@code error}: Error event from the API</li>
     * </ul>
     *
     * @param request the message creation request (should have {@code stream: true})
     * @param options options controlling what data to return (e.g., thinking outputs, server tool results)
     * @param handler the callback handler for streaming events
     * @see AnthropicCreateMessageOptions
     * @see StreamingChatResponseHandler
     */
    @Override
    public void createMessage(
            AnthropicCreateMessageRequest request,
            AnthropicCreateMessageOptions options,
            StreamingChatResponseHandler handler) {

        ServerSentEventListener eventListener = new ServerSentEventListener() {

            final List<String> contents = synchronizedList(new ArrayList<>());
            final StringBuffer contentBuilder = new StringBuffer();

            final List<String> thinkings = synchronizedList(new ArrayList<>());
            final StringBuffer thinkingBuilder = new StringBuffer();
            final List<String> thinkingSignatures = synchronizedList(new ArrayList<>());
            final List<String> redactedThinkings = synchronizedList(new ArrayList<>());

            volatile String currentContentBlockStartType;

            final ToolCallBuilder toolCallBuilder = new ToolCallBuilder(-1);
            final List<AnthropicServerToolResult> serverToolResults = synchronizedList(new ArrayList<>());

            final AtomicInteger inputTokenCount = new AtomicInteger();
            final AtomicInteger outputTokenCount = new AtomicInteger();

            final AtomicInteger cacheCreationInputTokens = new AtomicInteger();
            final AtomicInteger cacheReadInputTokens = new AtomicInteger();

            final AtomicReference<String> responseId = new AtomicReference<>();
            final AtomicReference<String> responseModel = new AtomicReference<>();

            volatile String stopReason;
            volatile StreamingHandle streamingHandle;

            final AtomicReference<SuccessfulHttpResponse> rawHttpResponse = new AtomicReference<>();
            final Queue<ServerSentEvent> rawServerSentEvents = new ConcurrentLinkedQueue<>();

            @Override
            public void onOpen(SuccessfulHttpResponse response) {
                rawHttpResponse.set(response);
            }

            @Override
            public void onEvent(ServerSentEvent event) {
                onEvent(event, new ServerSentEventContext(new CancellationUnsupportedHandle()));
            }

            @Override
            public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                if (streamingHandle == null) {
                    streamingHandle = toStreamingHandle(context.parsingHandle());
                }

                AnthropicStreamingData data = fromJson(event.data(), AnthropicStreamingData.class);

                if ("message_start".equals(event.event())) {
                    handleMessageStart(data);
                } else if ("content_block_start".equals(event.event())) {
                    handleContentBlockStart(data, streamingHandle);
                } else if ("content_block_delta".equals(event.event())) {
                    handleContentBlockDelta(data, streamingHandle);
                } else if ("content_block_stop".equals(event.event())) {
                    handleContentBlockStop(streamingHandle);
                } else if ("message_delta".equals(event.event())) {
                    handleMessageDelta(data);
                } else if ("message_stop".equals(event.event())) {
                    handleMessageStop();
                } else if ("error".equals(event.event())) {
                    handleError(event.data());
                }

                rawServerSentEvents.add(event);
            }

            private void handleMessageStart(AnthropicStreamingData data) {
                AnthropicResponseMessage message = data.message;
                if (message != null) {
                    if (message.usage != null) {
                        handleUsage(message.usage);
                    }
                    if (message.id != null) {
                        responseId.set(message.id);
                    }
                    if (message.model != null) {
                        responseModel.set(message.model);
                    }
                }
            }

            private void handleUsage(AnthropicUsage usage) {
                if (usage.inputTokens != null) {
                    this.inputTokenCount.set(usage.inputTokens);
                }
                if (usage.outputTokens != null) {
                    this.outputTokenCount.set(usage.outputTokens);
                }
                if (usage.cacheCreationInputTokens != null) {
                    this.cacheCreationInputTokens.set(usage.cacheCreationInputTokens);
                }
                if (usage.cacheReadInputTokens != null) {
                    this.cacheReadInputTokens.set(usage.cacheReadInputTokens);
                }
            }

            private void handleContentBlockStart(AnthropicStreamingData data, StreamingHandle streamingHandle) {
                if (data.contentBlock == null) {
                    return;
                }

                this.currentContentBlockStartType = data.contentBlock.type;

                if (CONTENT_BLOCK_TEXT.equals(currentContentBlockStartType)) {
                    String text = data.contentBlock.text;
                    if (isNotNullOrEmpty(text)) {
                        contentBuilder.append(text);
                        onPartialResponse(handler, text, streamingHandle);
                    }
                } else if (CONTENT_BLOCK_THINKING.equals(currentContentBlockStartType) && options.returnThinking()) {
                    String thinking = data.contentBlock.thinking;
                    if (isNotNullOrEmpty(thinking)) {
                        thinkingBuilder.append(thinking);
                        onPartialThinking(handler, thinking, streamingHandle);
                    }
                    String signature = data.contentBlock.signature;
                    if (isNotNullOrEmpty(signature)) {
                        thinkingSignatures.add(signature);
                    }
                } else if (CONTENT_BLOCK_REDACTED_THINKING.equals(currentContentBlockStartType)
                        && options.returnThinking()) {
                    String redactedThinking = data.contentBlock.data;
                    if (isNotNullOrEmpty(redactedThinking)) {
                        redactedThinkings.add(redactedThinking);
                    }
                } else if (CONTENT_BLOCK_TOOL_USE.equals(currentContentBlockStartType)) {
                    toolCallBuilder.updateIndex(toolCallBuilder.index() + 1);
                    toolCallBuilder.updateId(data.contentBlock.id);
                    toolCallBuilder.updateName(data.contentBlock.name);
                } else if (isServerToolResultType(currentContentBlockStartType) && options.returnServerToolResults()) {
                    AnthropicServerToolResult result = AnthropicServerToolResult.builder()
                            .type(data.contentBlock.type)
                            .toolUseId(data.contentBlock.toolUseId)
                            .content(data.contentBlock.content)
                            .build();
                    serverToolResults.add(result);
                }
            }

            private boolean isServerToolResultType(String type) {
                return type != null && type.endsWith("_tool_result");
            }

            private void handleContentBlockDelta(AnthropicStreamingData data, StreamingHandle streamingHandle) {
                if (data.delta == null) {
                    return;
                }

                if (CONTENT_BLOCK_TEXT.equals(currentContentBlockStartType)) {
                    String text = data.delta.text;
                    if (isNotNullOrEmpty(text)) {
                        contentBuilder.append(text);
                        onPartialResponse(handler, text, streamingHandle);
                    }
                } else if (CONTENT_BLOCK_THINKING.equals(currentContentBlockStartType) && options.returnThinking()) {
                    String thinking = data.delta.thinking;
                    if (isNotNullOrEmpty(thinking)) {
                        thinkingBuilder.append(thinking);
                        onPartialThinking(handler, thinking, streamingHandle);
                    }
                    String signature = data.delta.signature;
                    if (isNotNullOrEmpty(signature)) {
                        thinkingSignatures.add(signature);
                    }
                } else if (CONTENT_BLOCK_REDACTED_THINKING.equals(currentContentBlockStartType)
                        && options.returnThinking()) {
                    String redactedThinking = data.delta.data;
                    if (isNotNullOrEmpty(redactedThinking)) {
                        redactedThinkings.add(redactedThinking);
                    }
                } else if (CONTENT_BLOCK_TOOL_USE.equals(currentContentBlockStartType)) {
                    String partialJson = data.delta.partialJson;
                    if (isNotNullOrEmpty(partialJson)) {
                        toolCallBuilder.appendArguments(partialJson);

                        PartialToolCall partialToolRequest = PartialToolCall.builder()
                                .index(toolCallBuilder.index())
                                .id(toolCallBuilder.id())
                                .name(toolCallBuilder.name())
                                .partialArguments(partialJson)
                                .build();
                        onPartialToolCall(handler, partialToolRequest, streamingHandle);
                    }
                }
            }

            private void handleContentBlockStop(StreamingHandle streamingHandle) {
                if (CONTENT_BLOCK_TEXT.equals(currentContentBlockStartType)) {
                    contents.add(contentBuilder.toString());
                    contentBuilder.setLength(0);
                } else if (CONTENT_BLOCK_THINKING.equals(currentContentBlockStartType) && options.returnThinking()) {
                    thinkings.add(thinkingBuilder.toString());
                    thinkingBuilder.setLength(0);
                } else if (CONTENT_BLOCK_TOOL_USE.equals(currentContentBlockStartType)) {
                    CompleteToolCall completeToolCall = toolCallBuilder.buildAndReset();

                    if (completeToolCall.toolExecutionRequest().arguments().equals("{}")) {
                        PartialToolCall partialToolRequest = PartialToolCall.builder()
                                .index(completeToolCall.index())
                                .id(completeToolCall.toolExecutionRequest().id())
                                .name(completeToolCall.toolExecutionRequest().name())
                                .partialArguments(
                                        completeToolCall.toolExecutionRequest().arguments())
                                .build();
                        onPartialToolCall(handler, partialToolRequest, streamingHandle);
                    }

                    onCompleteToolCall(handler, completeToolCall);
                }
            }

            private void handleMessageDelta(AnthropicStreamingData data) {
                if (data.delta != null) {
                    AnthropicDelta delta = data.delta;
                    if (delta.stopReason != null) {
                        this.stopReason = delta.stopReason;
                    }
                }
                if (data.usage != null) {
                    handleUsage(data.usage);
                }
            }

            private void handleMessageStop() {
                ChatResponse completeResponse = build();
                onCompleteResponse(handler, completeResponse);
            }

            private ChatResponse build() {

                String text =
                        contents.stream().filter(content -> !content.isEmpty()).collect(joining("\n"));

                String thinking =
                        thinkings.stream().filter(content -> !content.isEmpty()).collect(joining("\n"));

                Map<String, Object> attributes = new HashMap<>();
                String thinkingSignature = thinkingSignatures.stream()
                        .filter(content -> !content.isEmpty())
                        .collect(joining("\n"));
                if (isNotNullOrBlank(thinkingSignature)) {
                    attributes.put(THINKING_SIGNATURE_KEY, thinkingSignature);
                }
                if (!redactedThinkings.isEmpty()) {
                    attributes.put(REDACTED_THINKING_KEY, redactedThinkings);
                }
                if (options.returnServerToolResults() && !serverToolResults.isEmpty()) {
                    attributes.put(SERVER_TOOL_RESULTS_KEY, serverToolResults);
                }

                List<ToolExecutionRequest> toolExecutionRequests = List.of();
                if (toolCallBuilder.hasRequests()) {
                    toolExecutionRequests = toolCallBuilder.allRequests();
                }

                AnthropicTokenUsage tokenUsage = AnthropicTokenUsage.builder()
                        .inputTokenCount(inputTokenCount.get())
                        .outputTokenCount(outputTokenCount.get())
                        .cacheCreationInputTokens(cacheCreationInputTokens.get())
                        .cacheReadInputTokens(cacheReadInputTokens.get())
                        .build();

                FinishReason finishReason = toFinishReason(stopReason);

                ChatResponseMetadata chatResponseMetadata = createMetadata(tokenUsage, finishReason);

                AiMessage aiMessage = AiMessage.builder()
                        .text(isNullOrEmpty(text) ? null : text)
                        .thinking(isNullOrEmpty(thinking) ? null : thinking)
                        .toolExecutionRequests(toolExecutionRequests)
                        .attributes(attributes)
                        .build();

                return ChatResponse.builder()
                        .aiMessage(aiMessage)
                        .metadata(chatResponseMetadata)
                        .build();
            }

            private ChatResponseMetadata createMetadata(AnthropicTokenUsage tokenUsage, FinishReason finishReason) {
                var metadataBuilder = AnthropicChatResponseMetadata.builder();
                if (responseId.get() != null) {
                    metadataBuilder.id(responseId.get());
                }
                if (responseModel.get() != null) {
                    metadataBuilder.modelName(responseModel.get());
                }
                if (tokenUsage != null) {
                    metadataBuilder.tokenUsage(tokenUsage);
                }
                if (finishReason != null) {
                    metadataBuilder.finishReason(finishReason);
                }
                if (rawHttpResponse.get() != null) {
                    metadataBuilder.rawHttpResponse(rawHttpResponse.get());
                }
                if (!rawServerSentEvents.isEmpty()) {
                    metadataBuilder.rawServerSentEvents(new ArrayList<>(rawServerSentEvents));
                }
                return metadataBuilder.build();
            }

            private void handleError(String dataString) {
                withLoggingExceptions(() -> handler.onError(new RuntimeException(dataString)));
            }

            @Override
            public void onError(Throwable error) {
                RuntimeException mappedError = ExceptionMapper.DEFAULT.mapException(error);
                withLoggingExceptions(() -> handler.onError(mappedError));
            }
        };

        HttpRequest httpRequest = toHttpRequest(toJson(request), "messages");

        httpClient.execute(httpRequest, eventListener);
    }

    /**
     * Counts the number of tokens in a message request.
     *
     * <p>Sends a request to the {@code /messages/count_tokens} endpoint to estimate
     * token usage before making an actual message creation request.</p>
     *
     * @param request the token counting request containing the messages and model
     * @return the response containing the token count
     * @throws RuntimeException if the HTTP request fails or the response cannot be parsed
     */
    @Override
    public MessageTokenCountResponse countTokens(AnthropicCountTokensRequest request) {
        HttpRequest httpRequest = toHttpRequest(toJson(request), "messages/count_tokens");
        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return fromJson(successfulHttpResponse.body(), MessageTokenCountResponse.class);
    }

    /**
     * Lists available models from the Anthropic API.
     *
     * <p>Sends a GET request to the {@code /models} endpoint to retrieve
     * information about available Claude models.</p>
     *
     * @return the response containing the list of available models
     * @throws RuntimeException if the HTTP request fails or the response cannot be parsed
     */
    @Override
    public AnthropicModelsListResponse listModels() {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(GET)
                .url(baseUrl, "models")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", version)
                .build();
        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return fromJson(successfulHttpResponse.body(), AnthropicModelsListResponse.class);
    }

    /**
     * Creates a message with streaming response handling using default options.
     *
     * <p>Convenience method that calls {@link #createMessage(AnthropicCreateMessageRequest, AnthropicCreateMessageOptions, StreamingChatResponseHandler)}
     * with default options (thinking outputs and server tool results disabled).</p>
     *
     * @param request the message creation request (should have {@code stream: true})
     * @param handler the callback handler for streaming events
     * @see #createMessage(AnthropicCreateMessageRequest, AnthropicCreateMessageOptions, StreamingChatResponseHandler)
     */
    public void createMessage(AnthropicCreateMessageRequest request, StreamingChatResponseHandler handler) {
        createMessage(request, new AnthropicCreateMessageOptions(false), handler);
    }

    /**
     * Converts a JSON request body and path into an HTTP request with required headers.
     *
     * <p>Constructs a POST request with the following headers:</p>
     * <ul>
     *   <li>{@code Content-Type: application/json}</li>
     *   <li>{@code x-api-key}: The configured API key</li>
     *   <li>{@code anthropic-version}: The configured API version</li>
     *   <li>{@code anthropic-beta}: The configured beta features (if set)</li>
     * </ul>
     *
     * @param jsonRequest the JSON-serialized request body
     * @param path the API endpoint path (e.g., "messages", "messages/count_tokens")
     * @return the constructed {@link HttpRequest}
     */
    private HttpRequest toHttpRequest(String jsonRequest, String path) {
        HttpRequest.Builder builder = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, path)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", version)
                .body(jsonRequest);

        if (this.beta != null) {
            builder.addHeader("anthropic-beta", beta);
        }

        return builder.build();
    }
}
