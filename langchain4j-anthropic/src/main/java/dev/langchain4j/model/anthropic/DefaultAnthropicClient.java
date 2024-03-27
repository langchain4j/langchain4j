package dev.langchain4j.model.anthropic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
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
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.AnthropicMapper.toFinishReason;
import static java.util.Collections.synchronizedList;

public class DefaultAnthropicClient extends AnthropicClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAnthropicClient.class);
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    private final AnthropicApi anthropicApi;
    private final OkHttpClient okHttpClient;

    private final String apiKey;
    private final String version;
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
                .baseUrl(ensureNotBlank(builder.baseUrl, "baseUrl"))
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();

        this.anthropicApi = retrofit.create(AnthropicApi.class);
    }

    @Override
    public AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request) {
        try {
            retrofit2.Response<AnthropicCreateMessageResponse> retrofitResponse
                    = anthropicApi.createMessage(apiKey, version, request).execute();
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

            final List<String> contents = synchronizedList(new ArrayList<>());
            volatile StringBuffer currentContentBuilder = new StringBuffer();

            final AtomicInteger inputTokenCount = new AtomicInteger();
            final AtomicInteger outputTokenCount = new AtomicInteger();

            volatile String stopReason;

            private synchronized StringBuffer currentContentBuilder() {
                return currentContentBuilder;
            }

            private synchronized void setCurrentContentBuilder(StringBuffer stringBuffer) {
                currentContentBuilder = stringBuffer;
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
                    AnthropicStreamingData data = GSON.fromJson(dataString, AnthropicStreamingData.class);

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
                if (data.message != null && data.message.usage != null) {
                    handleUsage(data.message.usage);
                }
            }

            private void handleUsage(AnthropicUsage usage) {
                if (usage.inputTokens != null) {
                    this.inputTokenCount.addAndGet(usage.inputTokens);
                }
                if (usage.outputTokens != null) {
                    this.outputTokenCount.addAndGet(usage.outputTokens);
                }
            }

            private void handleContentBlockStart(AnthropicStreamingData data) {
                if (data.contentBlock != null && "text".equals(data.contentBlock.type)) {
                    String text = data.contentBlock.text;
                    if (isNotNullOrEmpty(text)) {
                        currentContentBuilder().append(text);
                        handler.onNext(text);
                    }
                }
            }

            private void handleContentBlockDelta(AnthropicStreamingData data) {
                if (data.delta != null && "text_delta".equals(data.delta.type)) {
                    String text = data.delta.text;
                    if (isNotNullOrEmpty(text)) {
                        currentContentBuilder().append(text);
                        handler.onNext(text);
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
                Response<AiMessage> response = Response.from(
                        AiMessage.from(String.join("\n", contents)),
                        new TokenUsage(inputTokenCount.get(), outputTokenCount.get()),
                        toFinishReason(stopReason)
                );
                handler.onComplete(response);
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

        Call<ResponseBody> call = anthropicApi.streamMessage(apiKey, version, request);
        EventSources.createFactory(okHttpClient).newEventSource(call.request(), eventSourceListener);
    }
}
