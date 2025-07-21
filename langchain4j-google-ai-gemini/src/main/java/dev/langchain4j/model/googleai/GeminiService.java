package dev.langchain4j.model.googleai;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.googleai.Json.fromJson;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

class GeminiService {

    private static final String GEMINI_AI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";
    private static final String API_KEY_HEADER_NAME = "x-goog-api-key";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = ofSeconds(15);
    private static final Duration DEFAULT_READ_TIMEOUT = ofSeconds(60);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    GeminiService(
            final @Nullable HttpClientBuilder httpClientBuilder,
            final String apiKey,
            final String baseUrl,
            final boolean logRequestsAndResponses,
            final Duration timeout) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.baseUrl = getOrDefault(baseUrl, GeminiService.GEMINI_AI_ENDPOINT);
        final var builder = getOrDefault(httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient httpClient = builder.connectTimeout(
                        firstNotNull("connectTimeout", timeout, builder.connectTimeout(), DEFAULT_CONNECT_TIMEOUT))
                .readTimeout(firstNotNull("readTimeout", timeout, builder.readTimeout(), DEFAULT_READ_TIMEOUT))
                .build();

        if (logRequestsAndResponses) {
            this.httpClient = new LoggingHttpClient(httpClient, true, true);
        } else {
            this.httpClient = httpClient;
        }
    }

    GeminiGenerateContentResponse generateContent(String modelName, GeminiGenerateContentRequest request) {
        String url = String.format("%s/models/%s:generateContent", baseUrl, modelName);
        return sendRequest(url, apiKey, request, GeminiGenerateContentResponse.class);
    }

    GeminiCountTokensResponse countTokens(String modelName, GeminiCountTokensRequest request) {
        String url = String.format("%s/models/%s:countTokens", baseUrl, modelName);
        return sendRequest(url, apiKey, request, GeminiCountTokensResponse.class);
    }

    GoogleAiEmbeddingResponse embed(String modelName, GoogleAiEmbeddingRequest request) {
        String url = String.format("%s/models/%s:embedContent", baseUrl, modelName);
        return sendRequest(url, apiKey, request, GoogleAiEmbeddingResponse.class);
    }

    GoogleAiBatchEmbeddingResponse batchEmbed(String modelName, GoogleAiBatchEmbeddingRequest request) {
        String url = String.format("%s/models/%s:batchEmbedContents", baseUrl, modelName);
        return sendRequest(url, apiKey, request, GoogleAiBatchEmbeddingResponse.class);
    }

    void generateContentStream(
            String modelName,
            GeminiGenerateContentRequest request,
            boolean includeCodeExecutionOutput,
            StreamingChatResponseHandler handler) {
        String url = String.format("%s/models/%s:streamGenerateContent?alt=sse", baseUrl, modelName);
        streamRequest(url, apiKey, request, includeCodeExecutionOutput, handler);
    }

    private <T> T sendRequest(String url, String apiKey, Object requestBody, Class<T> responseType) {
        String jsonBody = Json.toJson(requestBody);
        HttpRequest request = buildHttpRequest(url, apiKey, jsonBody);

        SuccessfulHttpResponse response = httpClient.execute(request);

        return fromJson(response.body(), responseType);
    }

    private void streamRequest(
            String url,
            String apiKey,
            Object requestBody,
            boolean includeCodeExecutionOutput,
            StreamingChatResponseHandler handler) {
        String jsonBody = Json.toJson(requestBody);
        HttpRequest httpRequest = buildHttpRequest(url, apiKey, jsonBody);

        GeminiStreamingResponseBuilder responseBuilder = new GeminiStreamingResponseBuilder(includeCodeExecutionOutput);

        httpClient.execute(httpRequest, new ServerSentEventListener() {

            AtomicInteger toolIndex = new AtomicInteger(0);

            @Override
            public void onEvent(ServerSentEvent event) {
                GeminiGenerateContentResponse response = fromJson(event.data(), GeminiGenerateContentResponse.class);
                GeminiStreamingResponseBuilder.TextAndTools textAndTools = responseBuilder.append(response);
                textAndTools.maybeText().ifPresent(text -> {
                    onPartialResponse(handler, text);
                });

                for (ToolExecutionRequest tool : textAndTools.tools()) {
                    CompleteToolCall completeToolCall = new CompleteToolCall(toolIndex.get(), tool);
                    onCompleteToolCall(handler, completeToolCall);
                    toolIndex.incrementAndGet();
                }
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
