package dev.langchain4j.model.chatglm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.time.Duration;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.time.Duration.ofSeconds;

class ChatGlmClient {

    private final ChatGlmApi chatGLMApi;
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .create();


    @Builder
    public ChatGlmClient(String baseUrl, Duration timeout) {
        timeout = getOrDefault(timeout, ofSeconds(60));

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
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
