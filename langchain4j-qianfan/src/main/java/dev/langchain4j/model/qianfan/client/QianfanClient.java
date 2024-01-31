package dev.langchain4j.model.qianfan.client;


import dev.langchain4j.model.qianfan.client.chat.ChatCompletionRequest;
import dev.langchain4j.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.model.qianfan.client.chat.ChatTokenResponse;
import dev.langchain4j.model.qianfan.client.completion.CompletionRequest;
import dev.langchain4j.model.qianfan.client.completion.CompletionResponse;
import dev.langchain4j.model.qianfan.client.embedding.EmbeddingRequest;
import dev.langchain4j.model.qianfan.client.embedding.EmbeddingResponse;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;


public class QianfanClient {

    private static final Logger log = LoggerFactory.getLogger(QianfanClient.class);
    private final String baseUrl;
    private String token;
    private final OkHttpClient okHttpClient;
    private final QianfanApi qianfanApi;
    private final String apiKey;
    private final String secretKey;

    private final boolean logStreamingResponses;

    public static final String GRANT_TYPE = "client_credentials";
    public QianfanClient(String apiKey, String secretKey) {
        this(builder().apiKey(apiKey).secretKey(secretKey));
    }

    private QianfanClient(Builder serviceBuilder) {
        this.baseUrl = serviceBuilder.baseUrl;
        OkHttpClient.Builder okHttpClientBuilder = (new OkHttpClient.Builder()).callTimeout(serviceBuilder.callTimeout)
                .connectTimeout(serviceBuilder.connectTimeout).readTimeout(serviceBuilder.readTimeout)
                .writeTimeout(serviceBuilder.writeTimeout);
        if (serviceBuilder.apiKey == null) {
            throw new IllegalArgumentException("apiKey must be defined");
        } else if (serviceBuilder.secretKey == null) {
            throw new IllegalArgumentException("secretKey must be defined");
        } else {
            if (serviceBuilder.apiKey != null) {
                okHttpClientBuilder.addInterceptor(new AuthorizationHeaderInjector(serviceBuilder.apiKey));
            }

            if (serviceBuilder.proxy != null) {
                okHttpClientBuilder.proxy(serviceBuilder.proxy);
            }

            if (serviceBuilder.logRequests) {
                okHttpClientBuilder.addInterceptor(new RequestLoggingInterceptor());
            }

            if (serviceBuilder.logResponses) {
                okHttpClientBuilder.addInterceptor(new ResponseLoggingInterceptor());
            }

            this.logStreamingResponses = serviceBuilder.logStreamingResponses;
            this.apiKey = serviceBuilder.apiKey;
            this.secretKey = serviceBuilder.secretKey;
            this.okHttpClient = okHttpClientBuilder.build();
            Retrofit retrofit = (new Retrofit.Builder()).baseUrl(serviceBuilder.baseUrl).client(this.okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(Json.GSON)).build();
            this.qianfanApi = retrofit.create(QianfanApi.class);
        }
    }

    public void shutdown() {
        this.okHttpClient.dispatcher().executorService().shutdown();
        this.okHttpClient.connectionPool().evictAll();
        Cache cache = this.okHttpClient.cache();
        if (cache != null) {
            try {
                cache.close();
            } catch (IOException var3) {
                log.error("Failed to close cache", var3);
            }
        }

    }

    public static Builder builder() {
        return new Builder();
    }



    public SyncOrAsyncOrStreaming<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request,String endpoint) {
        refreshToken();

        return new RequestExecutor(this.qianfanApi.chatCompletions(endpoint,request, this.token), (r) -> {
            return r;
        }, this.okHttpClient, this.formatUrl("rpc/2.0/ai_custom/v1/wenxinworkshop/chat/"+endpoint+"?access_token="+this.token), () -> {
            return ChatCompletionRequest.builder().from(request).stream(true).build();
        }, ChatCompletionResponse.class, (r) -> {
            return r;
        }, this.logStreamingResponses);

    }




    public SyncOrAsyncOrStreaming<CompletionResponse> completion(CompletionRequest request, boolean stream,
                                                                 String endpoint) {
        refreshToken();
        CompletionRequest syncRequest = CompletionRequest.builder().from(request).stream(stream).build();
        return new RequestExecutor(this.qianfanApi.completions(endpoint,request, this.token), (r) -> {
            return r;
        }, this.okHttpClient, this.formatUrl("rpc/2.0/ai_custom/v1/wenxinworkshop/completions/"+endpoint+"?access_token="+this.token), () -> {
            return CompletionRequest.builder().from(request).stream(true).build();
        }, CompletionResponse.class, (r) -> {
            return r;
        }, this.logStreamingResponses);
    }



    public SyncOrAsync<EmbeddingResponse> embedding(EmbeddingRequest request, String serviceName) {
        refreshToken();
        return new RequestExecutor(this.qianfanApi.embeddings(serviceName, request, this.token), (r) -> {
            return r;
        });
    }


    private void refreshToken() {
        RequestExecutor<String, ChatTokenResponse, String> executor = new RequestExecutor<>(
                this.qianfanApi.getToken(GRANT_TYPE, this.apiKey,
                        this.secretKey), ChatTokenResponse::getAccessToken);
        this.token = executor.execute();

    }

    private String formatUrl(String endpoint) {
        return this.baseUrl + endpoint;
    }


    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private String secretKey;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;
        private Proxy proxy;
        private boolean logRequests;
        private boolean logResponses;
        private boolean logStreamingResponses;

        private Builder() {
            this.baseUrl = "https://aip.baidubce.com/";
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

        public Builder secretKey(String secretKey) {
            if (secretKey != null && !secretKey.trim().isEmpty()) {
                this.secretKey = secretKey;
                return this;
            } else {
                throw new IllegalArgumentException("secretKey cannot be null or empty. ");
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

        public Builder proxy(Proxy.Type type, String ip, int port) {
            this.proxy = new Proxy(type, new InetSocketAddress(ip, port));
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
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

        public Builder logStreamingResponses() {
            return this.logStreamingResponses(true);
        }

        public Builder logStreamingResponses(Boolean logStreamingResponses) {
            if (logStreamingResponses == null) {
                logStreamingResponses = false;
            }

            this.logStreamingResponses = logStreamingResponses;
            return this;
        }

        public QianfanClient build() {
            return new QianfanClient(this);
        }
    }
}
