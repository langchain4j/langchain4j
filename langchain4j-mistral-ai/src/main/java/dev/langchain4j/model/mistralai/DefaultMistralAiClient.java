package dev.langchain4j.model.mistralai;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
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
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.util.List;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.*;

class DefaultMistralAiClient extends MistralAiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMistralAiClient.class);
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    private final MistralAiApi mistralAiApi;
    private final OkHttpClient okHttpClient;
    private final boolean logStreamingResponses;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends MistralAiClient.Builder<DefaultMistralAiClient, Builder> {

        public DefaultMistralAiClient build() {
            return new DefaultMistralAiClient(this);
        }
    }

    DefaultMistralAiClient(Builder builder) {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(builder.timeout)
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder().addHeader("User-Agent", "LangChain4j").build()))
                .writeTimeout(builder.timeout);

        okHttpClientBuilder.addInterceptor(new MistralAiApiKeyInterceptor(builder.apiKey));

        if (builder.logRequests) {
            okHttpClientBuilder.addInterceptor(new MistralAiRequestLoggingInterceptor());
        }

        if (builder.logResponses) {
            okHttpClientBuilder.addInterceptor(new MistralAiResponseLoggingInterceptor());
        }

        this.logStreamingResponses = builder.logResponses;
        this.okHttpClient = okHttpClientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(formattedUrlForRetrofit(builder.baseUrl))
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();

        mistralAiApi = retrofit.create(MistralAiApi.class);
    }

    private static String formattedUrlForRetrofit(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Override
    public MistralAiChatCompletionResponse chatCompletion(MistralAiChatCompletionRequest request) {
        try {
            retrofit2.Response<MistralAiChatCompletionResponse> retrofitResponse
                    = mistralAiApi.chatCompletion(request).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void streamingChatCompletion(MistralAiChatCompletionRequest request, StreamingResponseHandler<AiMessage> handler) {
        EventSourceListener eventSourceListener = new EventSourceListener() {
            final StringBuffer contentBuilder = new StringBuffer();
            List<ToolExecutionRequest> toolExecutionRequests;
            TokenUsage tokenUsage;
            FinishReason finishReason;

            @Override
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                if (logStreamingResponses) {
                    LOGGER.debug("onOpen()");
                }
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if (logStreamingResponses) {
                    LOGGER.debug("onEvent() {}", data);
                }
                if ("[DONE]".equals(data)) {
                    AiMessage aiMessage;
                    if (!isNullOrEmpty(toolExecutionRequests)){
                        aiMessage = AiMessage.from(toolExecutionRequests);
                    } else {
                        aiMessage = AiMessage.from(contentBuilder.toString());
                    }

                    Response<AiMessage> response = Response.from(
                            aiMessage,
                            tokenUsage,
                            finishReason
                    );
                    handler.onComplete(response);
                } else {
                    try {
                        MistralAiChatCompletionResponse chatCompletionResponse = GSON.fromJson(data, MistralAiChatCompletionResponse.class);
                        MistralAiChatCompletionChoice choice = chatCompletionResponse.getChoices().get(0);

                        String chunk = choice.getDelta().getContent();
                        if (isNotNullOrBlank(chunk)) {
                            contentBuilder.append(chunk);
                            handler.onNext(chunk);
                        }

                        List<MistralAiToolCall> toolCalls = choice.getDelta().getToolCalls();
                        if (!isNullOrEmpty(toolCalls)) {
                            toolExecutionRequests = toToolExecutionRequests(toolCalls);
                        }

                        MistralAiUsage usageInfo = chatCompletionResponse.getUsage();
                        if (usageInfo != null) {
                            this.tokenUsage = tokenUsageFrom(usageInfo);
                        }

                        String finishReasonString = choice.getFinishReason();
                        if (finishReasonString != null) {
                            this.finishReason = finishReasonFrom(finishReasonString);
                        }
                    } catch (Exception e) {
                        handler.onError(e);
                        throw new RuntimeException(e);
                    }
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                if (logStreamingResponses) {
                    LOGGER.debug("onFailure()", t);
                }

                if (t != null) {
                    handler.onError(t);
                } else {
                    handler.onError(new RuntimeException(String.format("status code: %s; body: %s", response.code(), response.body())));
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                if (logStreamingResponses) {
                    LOGGER.debug("onClosed()");
                }
            }
        };

        EventSources.createFactory(this.okHttpClient)
                .newEventSource(
                        mistralAiApi.streamingChatCompletion(request).request(),
                        eventSourceListener);
    }

    @Override
    public MistralAiEmbeddingResponse embedding(MistralAiEmbeddingRequest request) {
        try {
            retrofit2.Response<MistralAiEmbeddingResponse> retrofitResponse
                    = mistralAiApi.embedding(request).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MistralAiModelResponse listModels() {
        try {
            retrofit2.Response<MistralAiModelResponse> retrofitResponse
                    = mistralAiApi.models().execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private RuntimeException toException(retrofit2.Response<?> retrofitResponse) throws IOException {
        int code = retrofitResponse.code();
        if (code >= 400) {
            ResponseBody errorBody = retrofitResponse.errorBody();
            if (errorBody != null) {
                String errorBodyString = errorBody.string();
                String errorMessage = String.format("status code: %s; body: %s", code, errorBodyString);
                LOGGER.error("Error response: {}", errorMessage);
                return new RuntimeException(errorMessage);
            }
        }
        return new RuntimeException(retrofitResponse.message());
    }
}
