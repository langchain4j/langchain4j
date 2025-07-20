package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicDelta;
import dev.langchain4j.model.anthropic.internal.api.AnthropicResponseMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicStreamingData;
import dev.langchain4j.model.anthropic.internal.api.AnthropicUsage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType.TEXT;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType.TOOL_USE;
import static dev.langchain4j.model.anthropic.internal.client.Json.fromJson;
import static dev.langchain4j.model.anthropic.internal.client.Json.toJson;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;
import static java.util.Collections.synchronizedList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Internal
public class DefaultAnthropicClient extends AnthropicClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String version;
    private final String beta;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AnthropicClient.Builder<DefaultAnthropicClient, Builder> {

        public DefaultAnthropicClient build() {
            return new DefaultAnthropicClient(this);
        }
    }

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
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.version = ensureNotBlank(builder.version, "version");
        this.beta = builder.beta;
    }

    @Override
    public AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request) {
        HttpRequest httpRequest = toHttpRequest(request);
        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return fromJson(successfulHttpResponse.body(), AnthropicCreateMessageResponse.class);
    }

    @Override
    public void createMessage(AnthropicCreateMessageRequest request, StreamingChatResponseHandler handler) {

        ServerSentEventListener eventListener = new ServerSentEventListener() {

            final ReentrantLock lock = new ReentrantLock();
            final List<String> contents = synchronizedList(new ArrayList<>());
            volatile StringBuffer currentContentBuilder = new StringBuffer();

            final AtomicReference<AnthropicContentBlockType> currentContentBlockStartType = new AtomicReference<>();
            final Map<Integer, AnthropicToolExecutionRequestBuilder> toolExecutionRequestBuilderMap = new ConcurrentHashMap<>();

            final AtomicInteger inputTokenCount = new AtomicInteger();
            final AtomicInteger outputTokenCount = new AtomicInteger();

            final AtomicInteger cacheCreationInputTokens = new AtomicInteger();
            final AtomicInteger cacheReadInputTokens = new AtomicInteger();

            final AtomicReference<String> responseId = new AtomicReference<>();
            final AtomicReference<String> responseModel = new AtomicReference<>();

            volatile String stopReason;

            private StringBuffer currentContentBuilder() {
                lock.lock();
                try {
                    return currentContentBuilder;
                } finally {
                    lock.unlock();
                }
            }

            private void setCurrentContentBuilder(StringBuffer stringBuffer) {
                lock.lock();
                try {
                    currentContentBuilder = stringBuffer;
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void onEvent(ServerSentEvent event) {
                AnthropicStreamingData data = fromJson(event.data(), AnthropicStreamingData.class);

                if ("message_start".equals(event.event())) {
                    handleMessageStart(data);
                } else if ("content_block_start".equals(event.event())) {
                    handleContentBlockStart(data);
                } else if ("content_block_delta".equals(event.event())) {
                    handleContentBlockDelta(data);
                } else if ("content_block_stop".equals(event.event())) {
                    handleContentBlockStop();
                } else if ("message_delta".equals(event.event())) {
                    handleMessageDelta(data);
                } else if ("message_stop".equals(event.event())) {
                    handleMessageStop();
                } else if ("error".equals(event.event())) {
                    handleError(event.data());
                }
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

            private void handleContentBlockStart(AnthropicStreamingData data) {
                if (data.contentBlock == null) {
                    return;
                }

                currentContentBlockStartType.set(data.contentBlock.type);

                if (currentContentBlockStartType.get() == TEXT) {
                    String text = data.contentBlock.text;
                    if (isNotNullOrEmpty(text)) {
                        currentContentBuilder().append(text);
                        try {
                            handler.onPartialResponse(text);
                        } catch (Exception e) {
                            withLoggingExceptions(() -> handler.onError(e));
                        }
                    }
                } else if (currentContentBlockStartType.get() == TOOL_USE) {
                    toolExecutionRequestBuilderMap.putIfAbsent(
                            data.index,
                            new AnthropicToolExecutionRequestBuilder(data.contentBlock.id, data.contentBlock.name)
                    );
                }
            }

            private void handleContentBlockDelta(AnthropicStreamingData data) {
                if (data.delta == null) {
                    return;
                }

                if (currentContentBlockStartType.get() == TEXT) {
                    String text = data.delta.text;
                    if (isNotNullOrEmpty(text)) {
                        currentContentBuilder().append(text);
                        try {
                            handler.onPartialResponse(text);
                        } catch (Exception e) {
                            withLoggingExceptions(() -> handler.onError(e));
                        }
                    }
                } else if (currentContentBlockStartType.get() == TOOL_USE) {
                    String partialJson = data.delta.partialJson;
                    if (isNotNullOrEmpty(partialJson)) {
                        Integer toolExecutionsIndex = data.index;
                        if (toolExecutionsIndex != null) {
                            AnthropicToolExecutionRequestBuilder toolExecutionRequestBuilder = toolExecutionRequestBuilderMap.get(toolExecutionsIndex);
                            toolExecutionRequestBuilder.appendArguments(partialJson);
                        }
                    }
                }
            }

            private void handleContentBlockStop() {
                contents.add(currentContentBuilder().toString());
                setCurrentContentBuilder(new StringBuffer());
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
                ChatResponse response = build();
                try {
                    handler.onCompleteResponse(response);
                } catch (Exception e) {
                    withLoggingExceptions(() -> handler.onError(e));
                }
            }

            private ChatResponse build() {

                String text = contents.stream()
                        .filter(content -> !content.isEmpty())
                        .collect(joining("\n"));

                AnthropicTokenUsage tokenUsage = AnthropicTokenUsage.builder()
                        .inputTokenCount(inputTokenCount.get())
                        .outputTokenCount(outputTokenCount.get())
                        .cacheCreationInputTokens(cacheCreationInputTokens.get())
                        .cacheReadInputTokens(cacheReadInputTokens.get())
                        .build();

                FinishReason finishReason = toFinishReason(stopReason);

                ChatResponseMetadata metadata = createMetadata(tokenUsage, finishReason);

                if (toolExecutionRequestBuilderMap.isEmpty()) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(text))
                            .metadata(metadata)
                            .build();
                } else {
                    List<ToolExecutionRequest> toolExecutionRequests = toolExecutionRequestBuilderMap
                            .values().stream()
                            .map(AnthropicToolExecutionRequestBuilder::build)
                            .collect(toList());

                    AiMessage aiMessage = isNullOrBlank(text)
                            ? AiMessage.from(toolExecutionRequests)
                            : AiMessage.from(text, toolExecutionRequests);

                    return ChatResponse.builder()
                            .aiMessage(aiMessage)
                            .metadata(metadata)
                            .build();
                }
            }

            private ChatResponseMetadata createMetadata(AnthropicTokenUsage tokenUsage, FinishReason finishReason) {
                var metadataBuilder = ChatResponseMetadata.builder();
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

        HttpRequest httpRequest = toHttpRequest(request);

        httpClient.execute(httpRequest, eventListener);
    }

    private HttpRequest toHttpRequest(AnthropicCreateMessageRequest request) {

        HttpRequest.Builder builder = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "messages")
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", version)
                .body(toJson(request));

        if (this.beta != null) {
            builder.addHeader("anthropic-beta", beta);
        }

        return builder.build();
    }
}
