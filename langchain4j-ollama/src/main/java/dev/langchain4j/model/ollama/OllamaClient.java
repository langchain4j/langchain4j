package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static java.lang.Boolean.TRUE;

@Slf4j
class OllamaClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(INDENT_OUTPUT);

    private final OllamaApi ollamaApi;
    private final boolean logStreamingResponses;

    @Builder
    public OllamaClient(String baseUrl,
                        Duration timeout,
                        Boolean logRequests, Boolean logResponses, Boolean logStreamingResponses,
                        Map<String, String> customHeaders) {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);
        if (logRequests != null && logRequests) {
            okHttpClientBuilder.addInterceptor(new OllamaRequestLoggingInterceptor());
        }
        if (logResponses != null && logResponses) {
            okHttpClientBuilder.addInterceptor(new OllamaResponseLoggingInterceptor());
        }
        this.logStreamingResponses = logStreamingResponses != null && logStreamingResponses;

        // add custom header interceptor
        if (customHeaders != null && !customHeaders.isEmpty()) {
            okHttpClientBuilder.addInterceptor(new GenericHeadersInterceptor(customHeaders));
        }
        OkHttpClient okHttpClient = okHttpClientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Utils.ensureTrailingForwardSlash(baseUrl))
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
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

                        if (logStreamingResponses) {
                            log.debug("Streaming partial response: {}", partialResponse);
                        }

                        CompletionResponse completionResponse = OBJECT_MAPPER.readValue(partialResponse, CompletionResponse.class);
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

                            if (logStreamingResponses) {
                                log.debug("Streaming partial response: {}", partialResponse);
                            }

                            ChatResponse chatResponse = OBJECT_MAPPER.readValue(partialResponse, ChatResponse.class);
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
            retrofit2.Response<EmbeddingResponse> retrofitResponse = ollamaApi.embed(request).execute();
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

    public RunningModelsListResponse listRunningModels() {
        try {
            retrofit2.Response<RunningModelsListResponse> retrofitResponse = ollamaApi.listRunningModels().execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Void deleteModel(DeleteModelRequest deleteModelRequest) {
        try {
            retrofit2.Response<Void> retrofitResponse = ollamaApi.deleteModel(deleteModelRequest).execute();
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

    static class GenericHeadersInterceptor implements Interceptor {

        private final Map<String, String> headers = new HashMap<>();

        GenericHeadersInterceptor(Map<String, String> headers) {
            Optional.ofNullable(headers)
                    .ifPresent(this.headers::putAll);
        }

        @NotNull
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request.Builder builder = chain.request().newBuilder();

            // Add headers
            this.headers.forEach(builder::addHeader);

            return chain.proceed(builder.build());
        }
    }
}
