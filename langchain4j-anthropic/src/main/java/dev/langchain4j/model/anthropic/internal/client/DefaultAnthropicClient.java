package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;

import java.time.Duration;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.anthropic.internal.client.AnthropicJsonUtils.fromJson;
import static dev.langchain4j.model.anthropic.internal.client.AnthropicJsonUtils.toJson;

public class DefaultAnthropicClient extends AnthropicClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AnthropicClient.Builder<DefaultAnthropicClient, Builder> {

        public DefaultAnthropicClient build() {
            return new DefaultAnthropicClient(this);
        }
    }

    DefaultAnthropicClient(Builder builder) {
        if (isNullOrBlank(builder.apiKey)) {
            throw new IllegalArgumentException("Anthropic API key must be defined. " +
                    "It can be generated here: https://console.anthropic.com/settings/keys");
        }

        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(
                        getOrDefault(builder.timeout, httpClientBuilder.connectTimeout()), Duration.ofSeconds(15)))
                .readTimeout(getOrDefault(
                        getOrDefault(builder.timeout, httpClientBuilder.readTimeout()), Duration.ofSeconds(60)))
                .build();

        if (builder.logRequests != null || builder.logResponses != null) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }
        this.baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");
        this.apiKey = builder.apiKey;
    }

    @Override
    public AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request) {
        ensureNotEmpty(request.getMessages(), "messages");

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "api/chat")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-anthropic-ai")
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);
        return fromJson(successfulHttpResponse.body(), AnthropicCreateMessageResponse.class);
    }

    @Override
    public void createMessage(AnthropicCreateMessageRequest request, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(request.getMessages(), "messages");

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "api/generate")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "langchain4j-anthropic-ai")
                .body(toJson(request))
                .build();

        AnthropicServerSentEventListener<AiMessage> listener =
                new AnthropicServerSentEventListener<>(handler, (content, toolExecutionRequests) -> {
                    if (!isNullOrEmpty(toolExecutionRequests)) {
                        return AiMessage.from(toolExecutionRequests);
                    } else {
                        return AiMessage.from(content);
                    }
                });
        httpClient.execute(httpRequest, new DefaultServerSentEventParser(), listener);
    }
}
