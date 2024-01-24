package dev.langchain4j.model.mistralai;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
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
import java.time.Duration;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.*;

class MistralAiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MistralAiClient.class);
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();
    private final MistralAiApi mistralAiApi;
    private final OkHttpClient okHttpClient;

    @Builder
    public MistralAiClient(String baseUrl,
                           String apiKey,
                           Duration timeout,
                           Boolean logRequests,
                           Boolean logResponses) {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("MistralAI API Key must be defined. It can be generated here: https://console.mistral.ai/user/api-keys/");
        }else {
            okHttpClientBuilder.addInterceptor(new MistralAiApiKeyInterceptor(apiKey));
            // Log raw HTTP requests
            if (logRequests) {
                okHttpClientBuilder.addInterceptor(new MistralAiRequestLoggingInterceptor());
            }

            // Log raw HTTP responses
            if (logResponses) {
                okHttpClientBuilder.addInterceptor(new MistralAiResponseLoggingInterceptor());
            }
        }

        this.okHttpClient = okHttpClientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();

        mistralAiApi = retrofit.create(MistralAiApi.class);
    }

    public MistralChatCompletionResponse chatCompletion(MistralChatCompletionRequest request) {
        try {
            retrofit2.Response<MistralChatCompletionResponse> retrofitResponse
                    = mistralAiApi.chatCompletion(request).execute();
            if (retrofitResponse.isSuccessful()) {
                LOGGER.debug("ChatCompletionResponseBody: {}", retrofitResponse.body());
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void streamingChatCompletion(MistralChatCompletionRequest request, StreamingResponseHandler<AiMessage> handler) {
        EventSourceListener eventSourceListener = new EventSourceListener() {
            final StringBuffer contentBuilder = new StringBuffer();
            TokenUsage tokenUsage;
            FinishReason finishReason;

            @Override
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                LOGGER.debug("onOpen()");
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {

                LOGGER.debug("onEvent() {}", data);
                if ("[DONE]".equals(data)) {
                    Response<AiMessage> response = Response.from(
                            AiMessage.from(contentBuilder.toString()),
                            tokenUsage,
                            finishReason
                    );
                    handler.onComplete(response);
                } else {
                    try {
                        MistralChatCompletionResponse chatCompletionResponse = GSON.fromJson(data, MistralChatCompletionResponse.class);
                        MistralChatCompletionChoice choice = chatCompletionResponse.getChoices().get(0);
                        String chunk = choice.getDelta().getContent();
                        contentBuilder.append(chunk);
                        handler.onNext(chunk);

                        MistralUsageInfo usageInfo = chatCompletionResponse.getUsage();
                        if(usageInfo != null){
                            this.tokenUsage = tokenUsageFrom(usageInfo);
                        }

                        String finishReasonString = choice.getFinishReason();
                        if(finishReasonString != null){
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
                LOGGER.debug("onFailure()", t);

                if (t != null){
                    handler.onError(t);
                } else {
                    handler.onError(new RuntimeException(String.format("status code: %s; body: %s", response.code(), response.body())));
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                LOGGER.debug("onClosed()");
            }

        };

        EventSources.createFactory(this.okHttpClient)
                .newEventSource(
                        mistralAiApi.streamingChatCompletion(request).request(),
                        eventSourceListener);
    }

    public MistralEmbeddingResponse embedding(MistralEmbeddingRequest request) {
        try {
            retrofit2.Response<MistralEmbeddingResponse> retrofitResponse
                    = mistralAiApi.embedding(request).execute();
            if (retrofitResponse.isSuccessful()) {
                LOGGER.debug("EmbeddingResponseBody: {}", retrofitResponse.body());
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MistralModelResponse listModels() {
        try {
            retrofit2.Response<MistralModelResponse> retrofitResponse
                    = mistralAiApi.models().execute();
            if (retrofitResponse.isSuccessful()) {
                LOGGER.debug("ModelResponseBody: {}", retrofitResponse.body());
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
