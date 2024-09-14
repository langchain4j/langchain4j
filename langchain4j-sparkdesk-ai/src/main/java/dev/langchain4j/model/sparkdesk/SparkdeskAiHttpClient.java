package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.sparkdesk.client.chat.http.HttpChatCompletionRequest;
import dev.langchain4j.model.sparkdesk.client.chat.http.HttpChatCompletionResponse;
import dev.langchain4j.model.sparkdesk.client.embedding.EmbeddingRequest;
import dev.langchain4j.model.sparkdesk.client.embedding.EmbeddingResponse;
import dev.langchain4j.model.sparkdesk.client.image.ImageRequest;
import dev.langchain4j.model.sparkdesk.client.image.ImageResponse;
import dev.langchain4j.model.sparkdesk.shared.Usage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.sparkdesk.DefaultSparkdeskAiHelper.toChatErrorResponse;
import static dev.langchain4j.model.sparkdesk.DefaultSparkdeskAiHelper.tokenUsageFrom;
import static dev.langchain4j.model.sparkdesk.Json.OBJECT_MAPPER;
import static retrofit2.converter.jackson.JacksonConverterFactory.create;

@Slf4j
public class SparkdeskAiHttpClient {
    private final SparkdeskAiApi sparkdeskAiApi;
    private final Boolean logResponses;

    public SparkdeskAiHttpClient(Builder builder) {
        String baseUrl = builder.baseUrl;
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(builder.callTimeout)
                .connectTimeout(builder.connectTimeout)
                .readTimeout(builder.readTimeout)
                .writeTimeout(builder.writeTimeout)
                .addInterceptor(new AuthorizationInterceptor(builder.apiKey, builder.apiSecret));

        if (builder.logRequests) {
            okHttpClientBuilder.addInterceptor(new RequestLoggingInterceptor());
        }

        this.logResponses = builder.logResponses;
        if (builder.logResponses) {
            okHttpClientBuilder.addInterceptor(new ResponseLoggingInterceptor());
        }

        OkHttpClient okHttpClient = okHttpClientBuilder.build();
        Retrofit retrofit = (new Retrofit.Builder())
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(create(OBJECT_MAPPER))
                .build();
        this.sparkdeskAiApi = retrofit.create(SparkdeskAiApi.class);
    }

    public static Builder builder() {
        return new Builder();
    }

    public HttpChatCompletionResponse chatCompletion(HttpChatCompletionRequest request) {
        try {
            retrofit2.Response<HttpChatCompletionResponse> retrofitResponse
                    = sparkdeskAiApi.chatCompletion(request).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            }
            return toChatErrorResponse(retrofitResponse);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public EmbeddingResponse embedAll(EmbeddingRequest request) {
        try {
            retrofit2.Response<EmbeddingResponse> responseResponse = sparkdeskAiApi.embeddings(request).execute();
            if (responseResponse.isSuccessful()) {
                return responseResponse.body();
            } else {
                throw toException(responseResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void streamingChatCompletion(HttpChatCompletionRequest request, StreamingResponseHandler<AiMessage> handler) {
        try {
            Response<ResponseBody> response = sparkdeskAiApi.streamChatCompletion(request).execute();
            if (response.isSuccessful()) {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    BufferedReader reader = null;
                    reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()));
                    HttpChatCompletionResponse last = null;
                    List<String> chunkList = new ArrayList<>();
                    String data;
                    HttpChatCompletionResponse chatCompletionResponse;
                    while ((data = reader.readLine()) != null) {
                        //逐行返回 并且每行要跳过前5个字符
                        if (data.length() > 5) {
                            String substring = data.substring(6);
                            if (this.logResponses) {
                                log.debug("stream data:{}", substring);
                            }
                            if ("[DONE]".equals(substring)) {
                                chatCompletionResponse = last;
                                Usage usage = chatCompletionResponse.getUsage();
                                AiMessage aiMessage = AiMessage.from(String.join("", chunkList));
                                TokenUsage tokenUsage = tokenUsageFrom(usage);
                                handler.onComplete(dev.langchain4j.model.output.Response.from(aiMessage, tokenUsage));
                            } else {
                                chatCompletionResponse = OBJECT_MAPPER.readValue(substring, HttpChatCompletionResponse.class);
                                String chunk = chatCompletionResponse.getChoices().get(0).getMessage().getContent();
                                chunkList.add(chunk);
                                handler.onNext(chunk);
                                last = chatCompletionResponse;
                            }
                        }
                    }
                    reader.close();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private RuntimeException toException(retrofit2.Response<?> retrofitResponse) throws IOException {
        int code = retrofitResponse.code();
        if (code >= 400) {
            try (ResponseBody errorBody = retrofitResponse.errorBody()) {
                if (errorBody != null) {
                    String errorBodyString = errorBody.string();
                    String errorMessage = String.format("status code: %s; body: %s", code, errorBodyString);
                    log.error("Error response: {}", errorMessage);
                    return new RuntimeException(errorMessage);
                }
            }
        }
        return new RuntimeException(retrofitResponse.message());
    }

    public ImageResponse imagesGeneration(ImageRequest request) {
        try {
            retrofit2.Response<ImageResponse> responseResponse = sparkdeskAiApi.generations(request).execute();
            if (responseResponse.isSuccessful()) {
                return responseResponse.body();
            } else {
                throw toException(responseResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Builder {
        private Boolean trial;
        private String baseUrl;
        private String apiKey;
        private String apiSecret;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;
        private boolean logRequests;
        private boolean logResponses;

        private Builder() {
            this.trial = Boolean.FALSE;
            this.baseUrl = "https://spark-api-open.xf-yun.com/v1/chat/completions";
            this.callTimeout = Duration.ofSeconds(60L);
            this.connectTimeout = Duration.ofSeconds(60L);
            this.readTimeout = Duration.ofSeconds(60L);
            this.writeTimeout = Duration.ofSeconds(60L);
        }

        public Builder baseUrl(String baseUrl) {
            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                return this;
            } else {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
        }

        public Builder apiKey(String apiKey) {
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                this.apiKey = apiKey;
                return this;
            } else {
                throw new IllegalArgumentException("apiKey cannot be null or empty. ");
            }
        }

        public Builder apiSecret(String apiSecret) {
            if (apiSecret != null && !apiSecret.trim().isEmpty()) {
                this.apiSecret = apiSecret;
                return this;
            } else {
                throw new IllegalArgumentException("apiSecret cannot be null or empty. ");
            }
        }


        public Builder callTimeout(Duration callTimeout) {
            if (callTimeout == null) {
                throw new IllegalArgumentException("callTimeout cannot be null");
            } else {
                this.callTimeout = callTimeout;
                return this;
            }
        }

        public Builder connectTimeout(Duration connectTimeout) {
            if (connectTimeout == null) {
                throw new IllegalArgumentException("connectTimeout cannot be null");
            } else {
                this.connectTimeout = connectTimeout;
                return this;
            }
        }

        public Builder readTimeout(Duration readTimeout) {
            if (readTimeout == null) {
                throw new IllegalArgumentException("readTimeout cannot be null");
            } else {
                this.readTimeout = readTimeout;
                return this;
            }
        }

        public Builder writeTimeout(Duration writeTimeout) {
            if (writeTimeout == null) {
                throw new IllegalArgumentException("writeTimeout cannot be null");
            } else {
                this.writeTimeout = writeTimeout;
                return this;
            }
        }

        public Builder logRequests() {
            return this.logRequests(true);
        }

        public Builder logRequests(Boolean logRequests) {
            if (logRequests == null) {
                logRequests = false;
            }

            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses() {
            return this.logResponses(true);
        }

        public Builder logResponses(Boolean logResponses) {
            if (logResponses == null) {
                logResponses = false;
            }

            this.logResponses = logResponses;
            return this;
        }

        public SparkdeskAiHttpClient build() {
            return new SparkdeskAiHttpClient(this);
        }
    }
}
