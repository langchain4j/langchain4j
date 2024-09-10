package dev.langchain4j.model.chatglm;

import dev.langchain4j.internal.Utils;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.Duration.ofSeconds;

class ChatGlmClient {

    private final ChatGlmApi chatGLMApi;

    public ChatGlmClient(String baseUrl,
                         Duration timeout,
                         boolean logRequestsAndResponses) {
        baseUrl = ensureNotNull(baseUrl, "baseUrl");
        timeout = getOrDefault(timeout, ofSeconds(60));

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);

        if (logRequestsAndResponses) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            okHttpClientBuilder.addInterceptor(loggingInterceptor);
        }

        OkHttpClient okHttpClient = okHttpClientBuilder.build();

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

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String baseUrl;
        private Duration timeout;
        private boolean logRequestsAndResponses;

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder logRequestsAndResponses(boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        ChatGlmClient build() {
            return new ChatGlmClient(baseUrl, timeout, logRequestsAndResponses);
        }
    }
}
