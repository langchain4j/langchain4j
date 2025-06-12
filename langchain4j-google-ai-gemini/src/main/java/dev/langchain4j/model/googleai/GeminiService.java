package dev.langchain4j.model.googleai;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.time.Duration;
import java.util.Optional;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.googleai.Json.fromJson;
import static java.time.Duration.ofSeconds;

class GeminiService {

    private static final String GEMINI_AI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";
    private static final String API_KEY_HEADER_NAME = "x-goog-api-key";

    private final HttpClient httpClient;

    GeminiService(HttpClientBuilder httpClientBuilder, boolean logRequestsAndResponses, Duration timeout) {
        httpClientBuilder = getOrDefault(httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(getOrDefault(timeout, httpClientBuilder.connectTimeout()), ofSeconds(15)))
                .readTimeout(getOrDefault(getOrDefault(timeout, httpClientBuilder.readTimeout()), ofSeconds(60)))
                .build();

        if (logRequestsAndResponses) {
            this.httpClient = new LoggingHttpClient(httpClient, true, true);
        } else {
            this.httpClient = httpClient;
        }
    }

    GeminiGenerateContentResponse generateContent(String modelName, String apiKey, GeminiGenerateContentRequest request) {
        String url = String.format("%s/models/%s:generateContent", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GeminiGenerateContentResponse.class);
    }

    GeminiCountTokensResponse countTokens(String modelName, String apiKey, GeminiCountTokensRequest request) {
        String url = String.format("%s/models/%s:countTokens", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GeminiCountTokensResponse.class);
    }

    GoogleAiEmbeddingResponse embed(String modelName, String apiKey, GoogleAiEmbeddingRequest request) {
        String url = String.format("%s/models/%s:embedContent", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GoogleAiEmbeddingResponse.class);
    }

    GoogleAiBatchEmbeddingResponse batchEmbed(String modelName, String apiKey, GoogleAiBatchEmbeddingRequest request) {
        String url = String.format("%s/models/%s:batchEmbedContents", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GoogleAiBatchEmbeddingResponse.class);
    }

    void generateContentStream(String modelName,
                               String apiKey,
                               GeminiGenerateContentRequest request,
                               boolean includeCodeExecutionOutput,
                               StreamingChatResponseHandler handler) {
        String url = String.format("%s/models/%s:streamGenerateContent?alt=sse", GEMINI_AI_ENDPOINT, modelName);
        streamRequest(url, apiKey, request, includeCodeExecutionOutput, handler);
    }

    private <T> T sendRequest(String url, String apiKey, Object requestBody, Class<T> responseType) {
        String jsonBody = Json.toJson(requestBody);
        HttpRequest request = buildHttpRequest(url, apiKey, jsonBody);

        SuccessfulHttpResponse response = httpClient.execute(request);

        return fromJson(response.body(), responseType);
    }

    private void streamRequest(String url,
                               String apiKey,
                               Object requestBody,
                               boolean includeCodeExecutionOutput,
                               StreamingChatResponseHandler handler) {
        String jsonBody = Json.toJson(requestBody);
        HttpRequest httpRequest = buildHttpRequest(url, apiKey, jsonBody);

        GeminiStreamingResponseBuilder responseBuilder = new GeminiStreamingResponseBuilder(includeCodeExecutionOutput);

        httpClient.execute(httpRequest, new ServerSentEventListener() {

            @Override
            public void onEvent(ServerSentEvent event) {
                GeminiGenerateContentResponse response = fromJson(event.data(), GeminiGenerateContentResponse.class);
                Optional<String> maybeText = responseBuilder.append(response);
                maybeText.ifPresent(text -> {
                    try {
                        handler.onPartialResponse(text);
                    } catch (Exception e) {
                        withLoggingExceptions(() -> handler.onError(e));
                    }
                });
            }

            @Override
            public void onClose() {
                ChatResponse chatResponse = responseBuilder.build();
                try {
                    handler.onCompleteResponse(chatResponse);
                } catch (Exception e) {
                    withLoggingExceptions(() -> handler.onError(e));
                }
            }

            @Override
            public void onError(Throwable error) {
                RuntimeException mappedError = ExceptionMapper.DEFAULT.mapException(error);
                withLoggingExceptions(() -> handler.onError(mappedError));
            }
        });
    }

    private HttpRequest buildHttpRequest(String url, String apiKey, String jsonBody) {
        return HttpRequest.builder()
                .method(POST)
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "LangChain4j")
                .addHeader(API_KEY_HEADER_NAME, apiKey)
                .body(jsonBody)
                .build();
    }
}
