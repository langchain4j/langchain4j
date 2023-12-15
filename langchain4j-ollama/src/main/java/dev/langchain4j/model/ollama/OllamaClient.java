package dev.langchain4j.model.ollama;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.time.Duration.ofSeconds;

class OllamaClient {

    private final OllamaApi ollamaApi;
    private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @Builder
    public OllamaClient(String baseUrl, Duration timeout) {
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

        ollamaApi = retrofit.create(OllamaApi.class);
    }

    public CompletionResponse completion(CompletionRequest request) {
        try {
            Response<CompletionResponse> retrofitResponse
                    = ollamaApi.completion(request).execute();

            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ChatResponse completion(ChatRequest request) {
        try {
            Response<ChatResponse> retrofitResponse
                    = ollamaApi.chat(request).execute();

            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void streamingCompletion(CompletionRequest request, StreamingResponseHandler<String> handler) {
        ollamaApi.streamingCompletion(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try (InputStream inputStream = response.body().byteStream()) {
                    StringBuilder content = new StringBuilder();
                    int inputTokenCount = 0;
                    int outputTokenCount = 0;
                    while (true) {
                        byte[] bytes = new byte[1024];
                        int len = inputStream.read(bytes);
                        String partialResponse = new String(bytes, 0, len);
                        CompletionResponse completionResponse = GSON.fromJson(partialResponse, CompletionResponse.class);

                        // finish streaming response
                        if (Boolean.TRUE.equals(completionResponse.getDone())) {
                            handler.onComplete(dev.langchain4j.model.output.Response.from(
                                    content.toString(),
                                    new TokenUsage(inputTokenCount, outputTokenCount)
                            ));
                            break;
                        }

                        // handle cur token and tokenUsage
                        content.append(completionResponse.getResponse());
                        inputTokenCount += Optional.ofNullable(completionResponse.getPromptEvalCount()).orElse(0);
                        outputTokenCount += Optional.ofNullable(completionResponse.getEvalCount()).orElse(0);
                        handler.onNext(completionResponse.getResponse());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {
                handler.onError(throwable);
            }
        });
    }

    public EmbeddingResponse embed(EmbeddingRequest request) {
        try {
            retrofit2.Response<EmbeddingResponse> retrofitResponse = ollamaApi.embedd(request).execute();
            if (retrofitResponse.isSuccessful()) {
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
