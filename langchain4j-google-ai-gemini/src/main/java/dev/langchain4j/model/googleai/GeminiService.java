package dev.langchain4j.model.googleai;

import static dev.langchain4j.http.client.HttpMethod.DELETE;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.http.client.sse.ServerSentEventParsingHandleUtils.toStreamingHandle;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.googleai.Json.fromJson;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.CancellationUnsupportedHandle;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.googleai.BatchRequestResponse.ListOperationsResponse;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiBatchEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiBatchEmbeddingResponse;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

class GeminiService {

    private static final String GEMINI_AI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";
    private static final String API_KEY_HEADER_NAME = "x-goog-api-key";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = ofSeconds(15);
    private static final Duration DEFAULT_READ_TIMEOUT = ofSeconds(60);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    enum BatchOperationType {
        BATCH_GENERATE_CONTENT("batchGenerateContent"),
        ASYNC_BATCH_EMBED_CONTENT("asyncBatchEmbedContent");

        private final String value;

        BatchOperationType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    GeminiService(
            final @Nullable HttpClientBuilder httpClientBuilder,
            final String apiKey,
            final String baseUrl,
            final boolean logRequestsAndResponses,
            final boolean logRequests,
            final boolean logResponses,
            final Logger logger,
            final Duration timeout) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.baseUrl = getOrDefault(baseUrl, GeminiService.GEMINI_AI_ENDPOINT);
        final var builder = getOrDefault(httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient httpClient = builder.connectTimeout(
                        firstNotNull("connectTimeout", timeout, builder.connectTimeout(), DEFAULT_CONNECT_TIMEOUT))
                .readTimeout(firstNotNull("readTimeout", timeout, builder.readTimeout(), DEFAULT_READ_TIMEOUT))
                .build();

        if (logRequestsAndResponses || logResponses || logRequests) {
            this.httpClient = new LoggingHttpClient(
                    httpClient,
                    logRequestsAndResponses || logRequests,
                    logRequestsAndResponses || logResponses,
                    logger);
        } else {
            this.httpClient = httpClient;
        }
    }

    GeminiGenerateContentResponse generateContent(String modelName, GeminiGenerateContentRequest request) {
        String url = String.format("%s/models/%s:generateContent", baseUrl, modelName);
        return sendRequest(url, apiKey, request, GeminiGenerateContentResponse.class);
    }

    @SuppressWarnings("unchecked")
    <REQ, RESP> BatchRequestResponse.Operation<RESP> batchCreate(
            String modelName, BatchRequestResponse.BatchCreateRequest<REQ> request, BatchOperationType operationType) {
        return (BatchRequestResponse.Operation<RESP>) sendRequest(
                String.format("%s/models/%s:%s", baseUrl, modelName, operationType.value),
                apiKey,
                request,
                BatchRequestResponse.Operation.class);
    }

    <REQ, RESP> BatchRequestResponse.Operation<RESP> batchCreate(
            String modelName, BatchRequestResponse.BatchCreateFileRequest request, BatchOperationType operationType) {
        return (BatchRequestResponse.Operation<RESP>) sendRequest(
                String.format("%s/models/%s:%s", baseUrl, modelName, operationType.value),
                apiKey,
                request,
                BatchRequestResponse.Operation.class);
    }

    @SuppressWarnings("unchecked")
    <RESP> BatchRequestResponse.Operation<RESP> batchRetrieveBatch(String operationName) {
        return (BatchRequestResponse.Operation<RESP>) sendRequest(
                String.format("%s/%s", baseUrl, operationName),
                apiKey,
                null,
                BatchRequestResponse.Operation.class,
                GET);
    }

    Void batchCancelBatch(String operationName) {
        String url = String.format("%s/%s:cancel", baseUrl, operationName);
        return sendRequest(url, apiKey, null, Void.class);
    }

    Void batchDeleteBatch(String batchName) {
        String url = String.format("%s/%s", baseUrl, batchName);
        return sendRequest(url, apiKey, null, Void.class, DELETE);
    }

    @SuppressWarnings("unchecked")
    <RESP> ListOperationsResponse<RESP> batchListBatches(@Nullable Integer pageSize, @Nullable String pageToken) {
        String url = buildUrl(
                baseUrl + "/batches",
                new StringPair("pageSize", pageSize != null ? String.valueOf(pageSize) : null),
                new StringPair("pageToken", pageToken));
        return sendRequest(url, apiKey, null, ListOperationsResponse.class, GET);
    }

    GeminiCountTokensResponse countTokens(String modelName, GeminiCountTokensRequest request) {
        String url = String.format("%s/models/%s:countTokens", baseUrl, modelName);
        return sendRequest(url, apiKey, request, GeminiCountTokensResponse.class);
    }

    GeminiEmbeddingResponse embed(String modelName, GeminiEmbeddingRequest request) {
        String url = String.format("%s/models/%s:embedContent", baseUrl, modelName);
        return sendRequest(url, apiKey, request, GeminiEmbeddingResponse.class);
    }

    GeminiBatchEmbeddingResponse batchEmbed(String modelName, GeminiBatchEmbeddingRequest request) {
        String url = String.format("%s/models/%s:batchEmbedContents", baseUrl, modelName);
        return sendRequest(url, apiKey, request, GeminiBatchEmbeddingResponse.class);
    }

    GeminiModelsListResponse listModels(@Nullable Integer pageSize, @Nullable String pageToken) {
        String url = buildUrl(
                baseUrl + "/models",
                new StringPair("pageSize", pageSize != null ? String.valueOf(pageSize) : null),
                new StringPair("pageToken", pageToken));
        return sendRequest(url, apiKey, null, GeminiModelsListResponse.class, GET);
    }

    void generateContentStream(
            String modelName,
            GeminiGenerateContentRequest request,
            boolean includeCodeExecutionOutput,
            Boolean returnThinking,
            StreamingChatResponseHandler handler) {
        String url = String.format("%s/models/%s:streamGenerateContent?alt=sse", baseUrl, modelName);
        streamRequest(url, apiKey, request, includeCodeExecutionOutput, returnThinking, handler);
    }

    private <T> T sendRequest(String url, String apiKey, @Nullable Object requestBody, Class<T> responseType) {
        return sendRequest(url, apiKey, requestBody, responseType, POST);
    }

    private <T> T sendRequest(
            String url, String apiKey, @Nullable Object requestBody, Class<T> responseType, HttpMethod httpMethod) {
        HttpRequest request = buildHttpRequest(url, apiKey, requestBody, httpMethod);
        return fromJson(httpClient.execute(request).body(), responseType);
    }

    private void streamRequest(
            String url,
            String apiKey,
            Object requestBody,
            boolean includeCodeExecutionOutput,
            Boolean returnThinking,
            StreamingChatResponseHandler handler) {
        HttpRequest httpRequest = buildHttpRequest(url, apiKey, requestBody, POST);

        GeminiStreamingResponseBuilder responseBuilder =
                new GeminiStreamingResponseBuilder(includeCodeExecutionOutput, returnThinking);

        httpClient.execute(httpRequest, new ServerSentEventListener() {

            AtomicInteger toolIndex = new AtomicInteger(0);
            volatile StreamingHandle streamingHandle;

            @Override
            public void onEvent(ServerSentEvent event) {
                onEvent(event, new ServerSentEventContext(new CancellationUnsupportedHandle()));
            }

            @Override
            public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                if (streamingHandle == null) {
                    streamingHandle = toStreamingHandle(context.parsingHandle());
                }

                GeminiGenerateContentResponse response = fromJson(event.data(), GeminiGenerateContentResponse.class);
                GeminiStreamingResponseBuilder.TextAndTools textAndTools = responseBuilder.append(response);
                textAndTools.maybeText().ifPresent(text -> {
                    onPartialResponse(handler, text, streamingHandle);
                });
                textAndTools.maybeThought().ifPresent(thought -> {
                    if (Boolean.TRUE.equals(returnThinking)) {
                        onPartialThinking(handler, thought, streamingHandle);
                    } else if (returnThinking == null) {
                        onPartialResponse(handler, thought, streamingHandle); // for backward compatibility
                    }
                });
                for (ToolExecutionRequest tool : textAndTools.tools()) {
                    CompleteToolCall completeToolCall = new CompleteToolCall(toolIndex.get(), tool);
                    onCompleteToolCall(handler, completeToolCall);
                    toolIndex.incrementAndGet();
                }
            }

            @Override
            public void onClose() {
                if (streamingHandle == null || !streamingHandle.isCancelled()) {
                    ChatResponse completeResponse = responseBuilder.build();
                    onCompleteResponse(handler, completeResponse);
                }
            }

            @Override
            public void onError(Throwable error) {
                RuntimeException mappedError = ExceptionMapper.DEFAULT.mapException(error);
                withLoggingExceptions(() -> handler.onError(mappedError));
            }
        });
    }

    private HttpRequest buildHttpRequest(String url, String apiKey, @Nullable Object body, HttpMethod method) {
        var builder = HttpRequest.builder()
                .method(method)
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "LangChain4j")
                .addHeader(API_KEY_HEADER_NAME, apiKey);
        if (body != null) {
            builder.body(Json.toJson(body));
        }
        return builder.build();
    }

    private static String buildUrl(String baseUrl, StringPair... pairs) {
        var queryParams = Stream.of(pairs)
                .filter(pair -> pair.value != null)
                .map(entry -> entry.key() + "=" + URLEncoder.encode(entry.value(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        return queryParams.isEmpty() ? baseUrl : baseUrl + "?" + queryParams;
    }

    private record StringPair(String key, @Nullable String value) {}
}
