package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.model.sparkdesk.client.chat.wss.WssChatCompletionRequest;
import dev.langchain4j.model.sparkdesk.client.chat.wss.WssChatCompletionResponse;
import dev.langchain4j.model.sparkdesk.client.chat.wss.WssResponseHeader;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class SparkdeskAiWssClient {

    private final String baseUrl;
    private final OkHttpClient okHttpClient;
    private final Boolean logResponses;


    public SparkdeskAiWssClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
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

        this.okHttpClient = okHttpClientBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @SneakyThrows
    public WssChatCompletionResponse chatCompletion(WssChatCompletionRequest wssChatCompletionRequest) {
        CompletableFuture<WssChatCompletionResponse> future = new CompletableFuture<>();
        Request request = new Request.Builder().url(this.baseUrl).build();
        okHttpClient.newWebSocket(request, new WebSocketListener() {

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                future.completeExceptionally(t);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                if (logResponses) {
                    log.debug("receive text:{}", text);
                }
                WssChatCompletionResponse response = Json.fromJson(text, WssChatCompletionResponse.class);
                WssResponseHeader header = response.getHeader();
                if (Objects.isNull(header)) {
                    webSocket.close(1000, "");
                    throw new RuntimeException("The response data is incomplete. SparkResponse. header is null, and the response returned by the server is:" + text);
                }

                Integer code = header.getCode();
                if (0 != code) {
                    webSocket.close(1000, "");
                    future.completeExceptionally(new RuntimeException("Service business exception, exception code:" + code));
                }

                //这里我感觉是把websocket当成http用了，根本没有维护长连接。官网上说只会返回一帧结果，那这里用websocket有什么用呢？？
                Integer status = header.getStatus();
                if (2 == status) {
                    webSocket.close(1000, "");
                    future.complete(response);
                }
            }

            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                webSocket.send(Json.toJson(wssChatCompletionRequest));
            }
        });
        return future.get();
    }

    public static class Builder {
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
            this.baseUrl = "wss://spark-api.xf-yun.com";
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


        public SparkdeskAiWssClient.Builder apiKey(String apiKey) {
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                this.apiKey = apiKey;
                return this;
            } else {
                throw new IllegalArgumentException("apiKey cannot be null or empty. ");
            }
        }

        public SparkdeskAiWssClient.Builder apiSecret(String apiSecret) {
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

        public SparkdeskAiWssClient build() {
            return new SparkdeskAiWssClient(this);
        }
    }
}
