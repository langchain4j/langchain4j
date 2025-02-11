package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.openai.internal.completion.CompletionResponse;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import dev.langchain4j.model.openai.internal.image.GenerateImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.moderation.ModerationRequest;
import dev.langchain4j.model.openai.internal.moderation.ModerationResponse;

import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

public class DefaultOpenAiClient extends OpenAiClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;
    private final String apiVersion; // TODO

    private DefaultOpenAiClient(Builder builder) {

        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(getOrDefault(builder.connectTimeout, httpClientBuilder.connectTimeout()), ofSeconds(15)))
                .readTimeout(getOrDefault(getOrDefault(builder.readTimeout, httpClientBuilder.readTimeout()), ofSeconds(60)))
                .build();

        if (builder.logRequests || builder.logResponses || builder.logStreamingResponses) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses || builder.logStreamingResponses); // TODO
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");
        this.apiVersion = builder.apiVersion;

        if (builder.openAiApiKey == null && builder.azureApiKey == null) {
            // TODO
            throw new IllegalArgumentException("openAiApiKey OR azureApiKey must be defined");
        }
        if (builder.openAiApiKey != null && builder.azureApiKey != null) {
            // TODO
            throw new IllegalArgumentException("openAiApiKey AND azureApiKey cannot both be defined at the same time");
        }

        Map<String, String> defaultHeaders = new HashMap<>();
        if (builder.openAiApiKey != null) {
            defaultHeaders.put("Authorization", "Bearer " + builder.openAiApiKey);
        }
        if (builder.azureApiKey != null) {
            // TODO !!! is "api-key" masked in generic logger?
            defaultHeaders.put("api-key", builder.openAiApiKey); // TODO test with azure?
        }
        if (builder.organizationId != null) {
            defaultHeaders.put("OpenAI-Organization", builder.organizationId);
        }
        if (builder.userAgent != null) {
            defaultHeaders.put("User-Agent", builder.userAgent);
        }
        if (builder.customHeaders != null) {
            defaultHeaders.putAll(builder.customHeaders);
        }
        this.defaultHeaders = defaultHeaders;

//        if (builder.proxy != null) { TODO
//            okHttpClientBuilder.proxy(builder.proxy);
//        }

//        if (builder.persistTo != null) {
//            retrofitBuilder.addConverterFactory(new PersistorConverterFactory(builder.persistTo));
//        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends OpenAiClient.Builder<DefaultOpenAiClient, Builder> {

        public DefaultOpenAiClient build() {
            return new DefaultOpenAiClient(this);
        }
    }

    @Override
    public SyncOrAsyncOrStreaming<CompletionResponse> completion(CompletionRequest request) {
        CompletionRequest syncRequest = CompletionRequest.builder().from(request).stream(false).build();

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "completions") // TODO apiVersion
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .body(Json.toJson(syncRequest))
                .build();

        return new RequestExecutor<>(
                httpClient,
                httpRequest,
                () -> CompletionRequest.builder().from(request).stream(true).build(),
                CompletionResponse.class
        );
    }

    @Override
    public SyncOrAsyncOrStreaming<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {
        ChatCompletionRequest syncRequest = ChatCompletionRequest.builder().from(request).stream(false).build();

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "chat/completions") // TODO apiVersion
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .body(Json.toJson(syncRequest))
                .build();

        return new RequestExecutor<>(
                httpClient,
                httpRequest,
                () -> ChatCompletionRequest.builder().from(request).stream(true).build(),
                ChatCompletionResponse.class
        );
    }

    @Override
    public SyncOrAsync<EmbeddingResponse> embedding(EmbeddingRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "embeddings") // TODO apiVersion
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .body(Json.toJson(request))
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, EmbeddingResponse.class);
    }

    @Override
    public SyncOrAsync<ModerationResponse> moderation(ModerationRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "moderations") // TODO apiVersion
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .body(Json.toJson(request))
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, ModerationResponse.class);
    }

    @Override
    public SyncOrAsync<GenerateImagesResponse> imagesGeneration(GenerateImagesRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "images/generations") // TODO apiVersion
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .body(Json.toJson(request))
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, GenerateImagesResponse.class);
    }

    private String formatUrl(String endpoint) { // TODO
        return baseUrl + endpoint + apiVersionQueryParam();
    }

    private String apiVersionQueryParam() { // TODO
        if (apiVersion == null || apiVersion.trim().isEmpty()) {
            return "";
        }
        return "?api-version=" + apiVersion;
    }
}
