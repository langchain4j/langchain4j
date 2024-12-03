package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.*;
import static dev.langchain4j.model.ollama.OllamaJsonUtils.getObjectMapper;
import static dev.langchain4j.model.ollama.OllamaJsonUtils.toObject;
import static java.lang.Boolean.TRUE;

class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final OllamaApi ollamaApi;
    private final boolean logStreamingResponses;

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
                .addConverterFactory(JacksonConverterFactory.create(getObjectMapper()))
                .build();

        ollamaApi = retrofit.create(OllamaApi.class);
    }

    static Builder builder() {
        return new Builder();
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
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        StringBuilder contentBuilder = new StringBuilder();
                        while (true) {
                            String partialResponse = reader.readLine();

                            if (logStreamingResponses) {
                                log.debug("Streaming partial response: {}", partialResponse);
                            }

                            CompletionResponse completionResponse = toObject(partialResponse, CompletionResponse.class);
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

    public void streamingChat(ChatRequest request, StreamingResponseHandler<AiMessage> handler,
                              List<ChatModelListener> listeners, List<ChatMessage> messages) {
        ChatModelRequest modelListenerRequest = createModelListenerRequest(request, messages, new ArrayList<>());
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        onListenRequest(listeners, modelListenerRequest, attributes);

        OllamaStreamingResponseBuilder responseBuilder = new OllamaStreamingResponseBuilder();
        ollamaApi.streamingChat(request).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> retrofitResponse) {
                try (InputStream inputStream = retrofitResponse.body().byteStream()) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        while (true) {
                            String partialResponse = reader.readLine();

                            if (logStreamingResponses) {
                                log.debug("Streaming partial response: {}", partialResponse);
                            }

                            ChatResponse chatResponse = toObject(partialResponse, ChatResponse.class);
                            String content = chatResponse.getMessage().getContent();
                            responseBuilder.append(chatResponse);
                            handler.onNext(content);

                            if (TRUE.equals(chatResponse.getDone())) {
                                Response<AiMessage> response = responseBuilder.build();
                                handler.onComplete(response);

                                onListenResponse(listeners, response, modelListenerRequest, attributes);

                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    onListenError(listeners, e, modelListenerRequest, responseBuilder.build(), attributes);

                    handler.onError(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable throwable) {
                onListenError(listeners, throwable, modelListenerRequest, responseBuilder.build(), attributes);

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

    static class Builder {

        private String baseUrl;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Boolean logStreamingResponses;
        private Map<String, String> customHeaders;

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        Builder logStreamingResponses(Boolean logStreamingResponses) {
            this.logStreamingResponses = logStreamingResponses;
            return this;
        }

        Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        OllamaClient build() {
            return new OllamaClient(baseUrl, timeout, logRequests, logResponses, logStreamingResponses, customHeaders);
        }
    }
}