package dev.langchain4j.model.chatglm;

import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.ModelConstant;
import lombok.Builder;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;

class ChatGlmClient {

    private final ChatGlmApi chatGLMApi;

    @Builder
    public ChatGlmClient(String baseUrl, Duration timeout) {
        timeout = getOrDefault(timeout, ModelConstant.DEFAULT_CLIENT_TIMEOUT);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Utils.ensureTrailingForwardSlash(baseUrl))
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();

        chatGLMApi = retrofit.create(ChatGlmApi.class);
    }

    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        try {
            Response<ChatCompletionResponse> retrofitResponse
                    = chatGLMApi.chatCompletion(request).execute();

            if (retrofitResponse.isSuccessful() && retrofitResponse.body() != null
                    && retrofitResponse.body().getStatus() == ChatGlmApi.OK) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private RuntimeException toException(Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();

        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }

}
