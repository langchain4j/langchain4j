package dev.langchain4j.model.anthropic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.AnthropicMapper.toFinishReason;
import static java.util.Collections.synchronizedList;

@Slf4j
class AnthropicClient {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    private final AnthropicApi anthropicApi;
    private final OkHttpClient okHttpClient;

    private final String apiKey;
    private final String version;
    private final boolean logResponses;

    @Builder
    AnthropicClient(String baseUrl,
                    String apiKey,
                    String version,
                    Duration timeout,
                    boolean logRequests,
                    boolean logResponses) {

        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("Anthropic API key must be defined. " +
                    "It can be generated here: https://console.anthropic.com/settings/keys");
        }

        this.apiKey = apiKey;
        this.version = ensureNotBlank(version, "version");
        this.logResponses = logResponses;

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);

        if (logRequests) {
            okHttpClientBuilder.addInterceptor(new AnthropicRequestLoggingInterceptor());
        }
        if (logResponses) {
            okHttpClientBuilder.addInterceptor(new AnthropicResponseLoggingInterceptor());
        }

        this.okHttpClient = okHttpClientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();

        this.anthropicApi = retrofit.create(AnthropicApi.class);
    }

    AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request) {
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

    void createMessage(AnthropicCreateMessageRequest request, StreamingResponseHandler<AiMessage> handler) {

        EventSourceListener eventSourceListener = new EventSourceListener() {

            final List<String> contents = synchronizedList(new ArrayList<>());
            volatile StringBuffer currentContentBuilder = new StringBuffer();

            final AtomicInteger inputTokenCount = new AtomicInteger();
            final AtomicInteger outputTokenCount = new AtomicInteger();

            volatile String stopReason;

            @Override
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                if (logResponses) {
                    log.debug("onOpen()");
                }
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String dataString) {
                if (logResponses) {
                    log.debug("onEvent() type: '{}', data: {}", type, dataString);
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
                if (usage.getInputTokens() != null) {
                    this.inputTokenCount.addAndGet(usage.getInputTokens());
                }
                if (usage.getOutputTokens() != null) {
                    this.outputTokenCount.addAndGet(usage.getOutputTokens());
                }
            }

            private void handleContentBlockStart(AnthropicStreamingData data) {
                if (data.contentBlock != null && "text".equals(data.contentBlock.getType())) {
                    String text = data.contentBlock.getText();
                    if (isNotNullOrEmpty(text)) {
                        currentContentBuilder.append(text);
                        handler.onNext(text);

                    }
                }
            }

            private void handleContentBlockDelta(AnthropicStreamingData data) {
                if (data.delta != null && "text_delta".equals(data.delta.type)) {
                    String text = data.delta.text;
                    if (isNotNullOrEmpty(text)) {
                        currentContentBuilder.append(text);
                        handler.onNext(text);

                    }
                }
            }

            private void handleContentBlockStop() {
                contents.add(currentContentBuilder.toString());
                currentContentBuilder = new StringBuffer();
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
                    log.debug("onFailure()", t);
                }

                if (t != null) {
                    handler.onError(t);
                } else if (response != null) {
                    try {
                        if (response.body() != null) {
                            handler.onError(new AnthropicHttpException(response.code(), response.body().string()));
                        }
                    } catch (IOException e) {
                        handler.onError(new AnthropicHttpException(response.code(), null));
                    }
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                if (logResponses) {
                    log.debug("onClosed()");
                }
            }
        };

        Call<ResponseBody> call = anthropicApi.streamCreateMessage(apiKey, version, request);
        EventSources.createFactory(okHttpClient).newEventSource(call.request(), eventSourceListener);
    }
}
