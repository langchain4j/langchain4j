package dev.langchain4j.model.mistralai;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
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

import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.*;
import static dev.langchain4j.model.output.FinishReason.*;

class MistralAiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MistralAiClient.class);
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();
    private final MistralAiApi mistralAiApi;
    private final OkHttpClient okHttpClient;

    @Builder
    public MistralAiClient(String baseUrl, String apiKey, Duration timeout) {
        okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new MistralAiApiKeyInterceptor(apiKey))
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();

        mistralAiApi = retrofit.create(MistralAiApi.class);
    }

    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        try {
            retrofit2.Response<ChatCompletionResponse> retrofitResponse
                    = mistralAiApi.chatCompletion(request).execute();
            LOGGER.debug("ChatCompletionResponse: {}", retrofitResponse);
            if (retrofitResponse.isSuccessful()) {
                LOGGER.error("ChatCompletionResponseBody: {}", retrofitResponse.body());
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void streamingChatCompletion(ChatCompletionRequest request, StreamingResponseHandler<AiMessage> handler) {
        EventSourceListener eventSourceListener = new EventSourceListener() {
            StringBuilder contentBuilder = new StringBuilder();
            UsageInfo tokenUsage = new UsageInfo();
            FinishReason lastFinishReason = null;

            @Override
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                LOGGER.debug("onOpen()");
                logResponse(response);
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {

                LOGGER.debug("onEvent() {}", data);
                if ("[DONE]".equals(data)) {
                    Response<AiMessage> response = Response.from(
                            AiMessage.from(contentBuilder.toString()),
                            tokenUsageFrom(tokenUsage),
                            lastFinishReason
                    );
                    handler.onComplete(response);
                } else {
                    try {
                        ChatCompletionResponse chatCompletionResponse = GSON.fromJson(data, ChatCompletionResponse.class);
                        ChatCompletionChoice choice = chatCompletionResponse.getChoices().get(0);
                        String chunk = choice.getDelta().getContent();
                        contentBuilder.append(chunk);
                        handler.onNext(chunk);

                        //Retrieving token usage of the last choice
                        if(choice.getFinishReason() != null){
                            FinishReason finishReason = finishReasonFrom(choice.getFinishReason());
                            switch (finishReason){
                                case STOP:
                                    lastFinishReason = STOP;
                                    tokenUsage = choice.getUsage();
                                    break;
                                case LENGTH:
                                    lastFinishReason = LENGTH;
                                    tokenUsage = choice.getUsage();
                                    break;
                                default:
                                     break;
                            }
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
                logResponse(response);

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

    public EmbeddingResponse embedding(EmbeddingRequest request) {
        try {
            retrofit2.Response<EmbeddingResponse> retrofitResponse
                    = mistralAiApi.embedding(request).execute();
            LOGGER.debug("EmbeddingResponse: {}", retrofitResponse);
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

    public ModelResponse listModels() {
        try {
            retrofit2.Response<ModelResponse> retrofitResponse
                    = mistralAiApi.models().execute();
            LOGGER.debug("ModelResponse: {}", retrofitResponse);
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
