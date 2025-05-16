package dev.langchain4j.model.mistralai.internal.client;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;
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

public class DefaultMistralAiClient extends MistralAiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMistralAiClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

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
                .baseUrl(Utils.ensureTrailingForwardSlash(builder.baseUrl))
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .build();

        mistralAiApi = retrofit.create(MistralAiApi.class);
    }

    @Override
    public MistralAiChatCompletionResponse chatCompletion(MistralAiChatCompletionRequest request) {
        return executeApiCall(mistralAiApi.chatCompletion(request));
    }

    @Override
    public void streamingChatCompletion(
            MistralAiChatCompletionRequest request, StreamingResponseHandler<AiMessage> handler) {
        executeStreamingApiCall(
                mistralAiApi.streamingChatCompletion(request), handler, (content, toolExecutionRequests) -> {
                    if (!isNullOrEmpty(toolExecutionRequests)) {
                        return AiMessage.from(toolExecutionRequests);
                    } else {
                        return AiMessage.from(content);
                    }
                });
    }

    @Override
    public MistralAiEmbeddingResponse embedding(MistralAiEmbeddingRequest request) {
        return executeApiCall(mistralAiApi.embedding(request));
    }

    @Override
    public MistralAiModerationResponse moderation(MistralAiModerationRequest request) {
        return executeApiCall(mistralAiApi.moderations(request));
    }

    @Override
    public MistralAiModelResponse listModels() {
        return executeApiCall(mistralAiApi.models());
    }

    @Override
    public MistralAiChatCompletionResponse fimCompletion(MistralAiFimCompletionRequest request) {
        return executeApiCall(mistralAiApi.fimCompletion(request));
    }

    @Override
    public void streamingFimCompletion(
            MistralAiFimCompletionRequest request, StreamingResponseHandler<String> handler) {
        executeStreamingApiCall(
                mistralAiApi.streamingFimCompletion(request), handler, (content, toolExecutionRequests) -> content);
    }

    private <T> T executeApiCall(Call<T> apiCall) {
        try {
            retrofit2.Response<T> retrofitResponse = apiCall.execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> void executeStreamingApiCall(
            Call<ResponseBody> apiCall,
            StreamingResponseHandler<T> handler,
            BiFunction<String, List<ToolExecutionRequest>, T> responseType) {
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
                    T responseContent = responseType.apply(contentBuilder.toString(), toolExecutionRequests);
                    Response<T> response = Response.from(responseContent, tokenUsage, finishReason);
                    handler.onComplete(response);
                } else {
                    try {
                        MistralAiChatCompletionResponse chatCompletionResponse =
                                OBJECT_MAPPER.readValue(data, MistralAiChatCompletionResponse.class);
                        MistralAiChatCompletionChoice choice =
                                chatCompletionResponse.getChoices().get(0);

                        String chunk = choice.getDelta().getContent();
                        if (isNotNullOrEmpty(chunk)) {
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
                    handler.onError(new RuntimeException(
                            String.format("status code: %s; body: %s", response.code(), response.body())));
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                if (logStreamingResponses) {
                    LOGGER.debug("onClosed()");
                }
            }
        };

        EventSources.createFactory(this.okHttpClient).newEventSource(apiCall.request(), eventSourceListener);
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
