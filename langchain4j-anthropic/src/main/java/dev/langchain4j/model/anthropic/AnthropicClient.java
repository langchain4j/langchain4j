package dev.langchain4j.model.anthropic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.time.Duration;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

class AnthropicClient {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    private final AnthropicApi anthropicApi;
    private final String apiKey;
    private final OkHttpClient okHttpClient;
    private final String version;

    @Builder
    AnthropicClient(String baseUrl,
                    String apiKey,
                    Duration timeout,
                    String version) {

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);

        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("AnthropicAI API Key must be defined. " +
                    "It can be generated here: https://console.anthropic.com/settings/keys");
        } else {
            this.apiKey = apiKey;
        }

        this.version = version;
        this.okHttpClient = okHttpClientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();

        anthropicApi = retrofit.create(AnthropicApi.class);
    }

    AnthropicChatResponse chatCompletion(AnthropicChatRequest request) {
        try {
            retrofit2.Response<AnthropicChatResponse> retrofitResponse
                    = anthropicApi.chatCompletion(version, apiKey, request).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RuntimeException toException(retrofit2.Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();
        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }

}
