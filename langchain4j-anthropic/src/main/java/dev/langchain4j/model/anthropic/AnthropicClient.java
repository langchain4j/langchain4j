package dev.langchain4j.model.anthropic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    @SneakyThrows
    void streamingMessages(AnthropicCreateMessageRequest request, StreamingResponseHandler<AiMessage> handler) {
        StringBuilder sb = new StringBuilder();
        int inputTokens = 0;
        Call<ResponseBody> call = anthropicApi.streamingMessages(apiKey, version, request);
        try (InputStream bodyStream = call.execute().body().byteStream()) {
            BufferedReader input = new BufferedReader(new InputStreamReader(bodyStream));
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String json = line.substring(5);
                    AnthropicCreateMessageResponse response = GSON.fromJson(json, AnthropicCreateMessageResponse.class);
                    if ("message_start".equals(response.getType())) {
                        inputTokens = response.getMessage().getUsage().getInputTokens();
                    }
                    else if ("content_block_delta".equals(response.getType()) && !response.getDelta().getText().isEmpty())  {
                        String next = response.getDelta().getText();
                        sb.append(next);
                        handler.onNext(next);
                    }
                    else if ("error".equals(response.getType())) {
                        handler.onError(new RuntimeException(response.getDelta().getText()));
                    } else if ("message_delta".equals(response.getType()) && response.getUsage() != null) {
                        TokenUsage tokenUsage = new TokenUsage(inputTokens, response.getUsage().getOutputTokens());
                        AiMessage aiMessage = AiMessage.aiMessage(sb.toString());
                        handler.onComplete(new dev.langchain4j.model.output.Response<>(aiMessage, tokenUsage, FinishReason.STOP));
                    }
                }
            }
        }
    }

    private static RuntimeException toException(Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();
        String errorMessage = format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
}
