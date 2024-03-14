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
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.lang.String.format;

class AnthropicClient {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    private final String apiKey;
    private final String version;
    private final AnthropicApi anthropicApi;

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

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .client(okHttpClientBuilder.build())
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
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RuntimeException toException(retrofit2.Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();
        String errorMessage = format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
}
