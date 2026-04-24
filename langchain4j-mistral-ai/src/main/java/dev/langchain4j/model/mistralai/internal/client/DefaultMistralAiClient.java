package dev.langchain4j.model.mistralai.internal.client;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.mistralai.internal.client.MistralAiJsonUtils.fromJson;
import static dev.langchain4j.model.mistralai.internal.client.MistralAiJsonUtils.toJson;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.*;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

@Internal
public class DefaultMistralAiClient extends MistralAiClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final Supplier<Map<String, String>> customHeadersSupplier;

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
            this.httpClient =
                    new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses, builder.logger);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.customHeadersSupplier = getOrDefault(builder.customHeadersSupplier, () -> Map::of);
    }

    private java.util.Map<String, String> buildRequestHeaders() {
        Map<String, String> dynamicHeaders = customHeadersSupplier.get();
        if (isNullOrEmpty(dynamicHeaders)) {
            return Map.of();
        }
        return dynamicHeaders;
    }

    @Override
    public MistralAiChatCompletionResponse chatCompletion(MistralAiChatCompletionRequest request) {
        return chatCompletionWithRawResponse(request).parsedResponse();
    }

    @Override
    public ParsedAndRawResponse<MistralAiChatCompletionResponse> chatCompletionWithRawResponse(
            MistralAiChatCompletionRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .addHeaders(buildRequestHeaders())
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse rawResponse = httpClient.execute(httpRequest);
        MistralAiChatCompletionResponse parsedResponse =
                fromJson(rawResponse.body(), MistralAiChatCompletionResponse.class);
        return new ParsedAndRawResponse<>(parsedResponse, rawResponse);
    }

    @Override
    public void streamingChatCompletion(MistralAiChatCompletionRequest request, StreamingChatResponseHandler handler) {
        streamingChatCompletion(request, handler, false);
    }

    @Override
    public void streamingChatCompletion(
            MistralAiChatCompletionRequest request, StreamingChatResponseHandler handler, boolean returnThinking) {
        ensureNotEmpty(request.getMessages(), "messages");

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .addHeaders(buildRequestHeaders())
                .body(toJson(request))
                .build();

        httpClient.execute(httpRequest, new MistralAiServerSentEventListener(handler, returnThinking));
    }

    @Override
    public MistralAiChatCompletionResponse fimCompletion(MistralAiFimCompletionRequest request) {
        return fimCompletionWithRawResponse(request).parsedResponse();
    }

    @Override
    public ParsedAndRawResponse<MistralAiChatCompletionResponse> fimCompletionWithRawResponse(
            MistralAiFimCompletionRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "fim/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .addHeaders(buildRequestHeaders())
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse rawResponse = httpClient.execute(httpRequest);
        MistralAiChatCompletionResponse parsedResponse =
                fromJson(rawResponse.body(), MistralAiChatCompletionResponse.class);
        return new ParsedAndRawResponse<>(parsedResponse, rawResponse);
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
                .addHeaders(buildRequestHeaders())
                .body(toJson(request))
                .build();

        MistralAiFimServerSentEventListener listener =
                new MistralAiFimServerSentEventListener(handler, (content, toolExecutionRequests) -> content);
        httpClient.execute(httpRequest, listener);
    }

    @Override
    public MistralAiEmbeddingResponse embedding(MistralAiEmbeddingRequest request) {
        return embeddingWithRawResponse(request).parsedResponse();
    }

    @Override
    public ParsedAndRawResponse<MistralAiEmbeddingResponse> embeddingWithRawResponse(
            MistralAiEmbeddingRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "embeddings")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .addHeaders(buildRequestHeaders())
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse rawResponse = httpClient.execute(httpRequest);
        MistralAiEmbeddingResponse parsedResponse = fromJson(rawResponse.body(), MistralAiEmbeddingResponse.class);
        return new ParsedAndRawResponse<>(parsedResponse, rawResponse);
    }

    @Override
    public MistralAiModerationResponse moderation(MistralAiModerationRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "moderations")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-mistral-ai")
                .addHeaders(buildRequestHeaders())
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
                .addHeaders(buildRequestHeaders())
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return fromJson(successfulHttpResponse.body(), MistralAiModelResponse.class);
    }
}
