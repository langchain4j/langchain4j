package dev.langchain4j.model.anthropic.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.internal.api.AnthropicApi;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicDelta;
import dev.langchain4j.model.anthropic.internal.api.AnthropicResponseMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicStreamingData;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType.TEXT;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType.TOOL_USE;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;
import static java.util.Collections.synchronizedList;
import static java.util.stream.Collectors.toList;

public class DefaultAnthropicClient extends AnthropicClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAnthropicClient.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    private final AnthropicApi anthropicApi;
    private final OkHttpClient okHttpClient;

    private final String apiKey;
    private final String version;
    private final String beta;
    private final boolean logResponses;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AnthropicClient.Builder<DefaultAnthropicClient, Builder> {

        public DefaultAnthropicClient build() {
            return new DefaultAnthropicClient(this);
        }
    }

    DefaultAnthropicClient(Builder builder) {
        if (isNullOrBlank(builder.apiKey)) {
            throw new IllegalArgumentException("Anthropic API key must be defined. " +
                    "It can be generated here: https://console.anthropic.com/settings/keys");
        }

        this.apiKey = builder.apiKey;
        this.version = ensureNotBlank(builder.version, "version");
        this.beta = builder.beta;
        this.logResponses = builder.logResponses;

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(builder.timeout)
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .writeTimeout(builder.timeout);

        if (builder.logRequests) {
            okHttpClientBuilder.addInterceptor(new AnthropicRequestLoggingInterceptor());
        }
        if (logResponses) {
            okHttpClientBuilder.addInterceptor(new AnthropicResponseLoggingInterceptor());
        }

        this.okHttpClient = okHttpClientBuilder.build();


        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Utils.ensureTrailingForwardSlash(ensureNotBlank(builder.baseUrl, "baseUrl")))
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .build();

        this.anthropicApi = retrofit.create(AnthropicApi.class);
    }

    @Override
    public AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request) {
        try {
            retrofit2.Response<AnthropicCreateMessageResponse> retrofitResponse
                    = anthropicApi.createMessage(apiKey, version, beta, request).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                try (ResponseBody errorBody = retrofitResponse.errorBody()) {
                    if (errorBody != null) {
                        throw new AnthropicHttpException(retrofitResponse.code(), errorBody.string());
                    }
                }
                throw new AnthropicHttpException(retrofitResponse.code(), null);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createMessage(AnthropicCreateMessageRequest request, StreamingResponseHandler<AiMessage> handler) {

        EventSourceListener eventSourceListener = new EventSourceListener() {

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
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                if (logResponses) {
                    LOGGER.debug("onOpen()");
                }
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String dataString) {
                if (logResponses) {
                    LOGGER.debug("onEvent() type: '{}', data: {}", type, dataString);
                }

                try {
                    AnthropicStreamingData data = OBJECT_MAPPER.readValue(dataString, AnthropicStreamingData.class);

                    if ("message_start".equals(type)) {
                        handleMessageStart(data);
                    } else if ("content_block_start".equals(type)) {
                        handleContentBlockStart(data);
                    } else if ("content_block_delta".equals(type)) {
                        handleContentBlockDelta(data);
                    } else if ("content_block_stop".equals(type)) {
                        handleContentBlockStop();
                    } else if ("message_delta".equals(type)) {
                        handleMessageDelta(data);
                    } else if ("message_stop".equals(type)) {
                        handleMessageStop();
                    } else if ("error".equals(type)) {
                        handleError(dataString);
                    }
                } catch (Exception e) {
                    handler.onError(e);
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
                    this.inputTokenCount.addAndGet(usage.inputTokens);
                }
                if (usage.outputTokens != null) {
                    this.outputTokenCount.addAndGet(usage.outputTokens);
                }
                if (usage.cacheCreationInputTokens != null) {
                    this.cacheCreationInputTokens.addAndGet(usage.cacheCreationInputTokens);
                }
                if (usage.cacheReadInputTokens != null) {
                    this.cacheReadInputTokens.addAndGet(usage.cacheReadInputTokens);
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
                        handler.onNext(text);
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
                        handler.onNext(text);
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
                Response<AiMessage> response = build();
                handler.onComplete(response);
            }

            private Response<AiMessage> build() {

                String text = String.join("\n", contents);
                TokenUsage tokenUsage = new AnthropicTokenUsage(inputTokenCount.get(), outputTokenCount.get(), cacheCreationInputTokens.get(), cacheReadInputTokens.get());
                FinishReason finishReason = toFinishReason(stopReason);
                Map<String, Object> metadata = createMetadata();

                if (toolExecutionRequestBuilderMap.isEmpty()) {
                    return Response.from(
                            AiMessage.from(text),
                            tokenUsage,
                            finishReason,
                            metadata
                    );
                } else {
                    List<ToolExecutionRequest> toolExecutionRequests = toolExecutionRequestBuilderMap
                            .values().stream()
                            .map(AnthropicToolExecutionRequestBuilder::build)
                            .collect(toList());

                    AiMessage aiMessage = isNullOrBlank(text)
                            ? AiMessage.from(toolExecutionRequests)
                            : AiMessage.from(text, toolExecutionRequests);

                    return Response.from(
                            aiMessage,
                            tokenUsage,
                            finishReason,
                            metadata
                    );
                }
            }

            private Map<String, Object> createMetadata() {
                Map<String, Object> metadata = new HashMap<>();
                if (responseId.get() != null) {
                    metadata.put("id", responseId.get());
                }
                if (responseModel.get() != null) {
                    metadata.put("model", responseModel.get());
                }
                return metadata;
            }

            private void handleError(String dataString) {
                handler.onError(new AnthropicHttpException(null, dataString));
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                if (logResponses) {
                    LOGGER.debug("onFailure()", t);
                }

                if (t != null) {
                    handler.onError(t);
                }

                if (response != null) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            handler.onError(new AnthropicHttpException(response.code(), responseBody.string()));
                        } else {
                            handler.onError(new AnthropicHttpException(response.code(), null));
                        }
                    } catch (IOException e) {
                        handler.onError(new AnthropicHttpException(response.code(), "[error reading response body]"));
                    }
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                if (logResponses) {
                    LOGGER.debug("onClosed()");
                }
            }
        };

        Call<ResponseBody> call = anthropicApi.streamMessage(apiKey, version, beta, request);
        EventSources.createFactory(okHttpClient).newEventSource(call.request(), eventSourceListener);
    }
}
