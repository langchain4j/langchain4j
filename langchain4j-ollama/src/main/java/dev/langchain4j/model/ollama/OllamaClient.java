package dev.langchain4j.model.ollama;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static java.lang.Boolean.TRUE;

class OllamaClient {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final OllamaApi ollamaApi;

    @Builder
    public OllamaClient(String baseUrl, Duration timeout) {

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
            retrofit2.Response<CompletionResponse> retrofitResponse
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

    public ChatResponse chat(ChatRequest request) {
        try {
            retrofit2.Response<ChatResponse> retrofitResponse
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
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> retrofitResponse) {
                try (InputStream inputStream = retrofitResponse.body().byteStream()) {
                    StringBuilder contentBuilder = new StringBuilder();
                    while (true) {
                        byte[] bytes = new byte[1024];
                        int len = inputStream.read(bytes);
                        String partialResponse = new String(bytes, 0, len);
                        CompletionResponse completionResponse = GSON.fromJson(partialResponse, CompletionResponse.class);

                        contentBuilder.append(completionResponse.getResponse());
                        handler.onNext(completionResponse.getResponse());

                        if (TRUE.equals(completionResponse.getDone())) {
                            Response<String> response = Response.from(
                                    contentBuilder.toString(),
                                    new TokenUsage(
                                            completionResponse.getPromptEvalCount(),
                                            completionResponse.getEvalCount()
                                    )
                            );
                            handler.onComplete(response);
                            return;
                        }
                    }
                } catch (Exception e) {
                    handler.onError(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {
                handler.onError(throwable);
            }
        });
    }

    public void streamingChat(ChatRequest request, StreamingResponseHandler<AiMessage> handler) {
        ollamaApi.streamingChat(request).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> retrofitResponse) {
                try (InputStream inputStream = retrofitResponse.body().byteStream()) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        StringBuilder contentBuilder = new StringBuilder();
                        while (true) {
                            String partialResponse = reader.readLine();
                            ChatResponse chatResponse = GSON.fromJson(partialResponse, ChatResponse.class);

                            String content = chatResponse.getMessage().getContent();
                            contentBuilder.append(content);
                            handler.onNext(content);

                            if (TRUE.equals(chatResponse.getDone())) {
                                Response<AiMessage> response = Response.from(
                                        AiMessage.from(contentBuilder.toString()),
                                        new TokenUsage(
                                                chatResponse.getPromptEvalCount(),
                                                chatResponse.getEvalCount()
                                        )
                                );
                                handler.onComplete(response);
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    handler.onError(e);
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

    public ModelsListResponse listModels() {
        try {
            retrofit2.Response<ModelsListResponse> retrofitResponse = ollamaApi.listModels().execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public OllamaModelCard showInformation(ShowModelInformationRequest showInformationRequest) {
        try {
            retrofit2.Response<OllamaModelCard> retrofitResponse = ollamaApi.showInformation(showInformationRequest).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private RuntimeException toException(retrofit2.Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();

        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
}
