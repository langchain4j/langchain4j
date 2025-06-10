package dev.langchain4j.model.mistralai.internal.client;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.mistralai.internal.client.MistralAiJsonUtils.fromJson;
import static dev.langchain4j.model.mistralai.internal.client.MistralAiJsonUtils.toJson;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.*;
import java.time.Duration;

public class DefaultMistralAiClient extends MistralAiClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends MistralAiClient.Builder<DefaultMistralAiClient, Builder> {

        public DefaultMistralAiClient build() {
            return new DefaultMistralAiClient(this);
        }
    }

    DefaultMistralAiClient(Builder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(
                        getOrDefault(builder.timeout, httpClientBuilder.connectTimeout()), Duration.ofSeconds(15)))
                .readTimeout(getOrDefault(
                        getOrDefault(builder.timeout, httpClientBuilder.readTimeout()), Duration.ofSeconds(60)))
                .build();

        if (builder.logRequests != null && builder.logRequests
                || builder.logResponses != null && builder.logResponses) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
    }

    @Override
    public MistralAiChatCompletionResponse chatCompletion(MistralAiChatCompletionRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return fromJson(successfulHttpResponse.body(), MistralAiChatCompletionResponse.class);
    }

    @Override
    public void streamingChatCompletion(
            MistralAiChatCompletionRequest request, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(request.getMessages(), "messages");

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .body(toJson(request))
                .build();

        MistralAiServerSentEventListener<AiMessage> listener =
                new MistralAiServerSentEventListener<>(handler, (content, toolExecutionRequests) -> {
                    if (!isNullOrEmpty(toolExecutionRequests)) {
                        return AiMessage.from(toolExecutionRequests);
                    } else {
                        return AiMessage.from(content);
                    }
                });
        httpClient.execute(httpRequest, listener);
    }

    @Override
    public MistralAiChatCompletionResponse fimCompletion(MistralAiFimCompletionRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "fim/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return fromJson(successfulHttpResponse.body(), MistralAiChatCompletionResponse.class);
    }

    @Override
    public void streamingFimCompletion(
            MistralAiFimCompletionRequest request, StreamingResponseHandler<String> handler) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "fim/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .body(toJson(request))
                .build();

        MistralAiServerSentEventListener<String> listener =
                new MistralAiServerSentEventListener<>(handler, (content, toolExecutionRequests) -> content);
        httpClient.execute(httpRequest, listener);
    }

    @Override
    public MistralAiEmbeddingResponse embedding(MistralAiEmbeddingRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "embeddings")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return fromJson(successfulHttpResponse.body(), MistralAiEmbeddingResponse.class);
    }

    @Override
    public MistralAiModerationResponse moderation(MistralAiModerationRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "moderations")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return fromJson(successfulHttpResponse.body(), MistralAiModerationResponse.class);
    }

    @Override
    public MistralAiModelResponse listModels() {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(HttpMethod.GET)
                .url(baseUrl, "models")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return fromJson(successfulHttpResponse.body(), MistralAiModelResponse.class);
    }
}
