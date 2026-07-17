package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.function.Consumer;
import java.util.function.Function;

class RequestExecutor<Response> implements SyncOrAsyncOrStreaming<Response> {

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final HttpRequest streamingHttpRequest;
    private final Class<Response> responseClass;
    private final Function<SuccessfulHttpResponse, Response> responseMapper;

    RequestExecutor(HttpClient httpClient, HttpRequest httpRequest, Class<Response> responseClass) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.streamingHttpRequest = null;
        this.responseClass = responseClass;
        this.responseMapper = null;
    }

    RequestExecutor(
            HttpClient httpClient,
            HttpRequest httpRequest,
            HttpRequest streamingHttpRequest,
            Class<Response> responseClass) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.streamingHttpRequest = streamingHttpRequest;
        this.responseClass = responseClass;
        this.responseMapper = null;
    }

    /**
     * Creates an executor that maps the raw HTTP response into the result instead of deserializing JSON.
     * Used for endpoints that return a non-JSON body (e.g. binary audio).
     */
    RequestExecutor(
            HttpClient httpClient,
            HttpRequest httpRequest,
            Function<SuccessfulHttpResponse, Response> responseMapper) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.streamingHttpRequest = null;
        this.responseClass = null;
        this.responseMapper = responseMapper;
    }

    @Override
    public Response execute() {
        return executeRaw().parsedResponse();
    }

    @Override
    public ParsedAndRawResponse<Response> executeRaw() {
        SyncRequestExecutor<Response> executor =
                new SyncRequestExecutor<>(httpClient, httpRequest, responseClass, responseMapper);
        return executor.execute();
    }

    @Override
    public AsyncResponseHandling onResponse(Consumer<Response> responseHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StreamingResponseHandling onPartialResponse(Consumer<Response> handler) {
        return onRawPartialResponse(parsedAndRawResponse -> handler.accept(parsedAndRawResponse.parsedResponse()));
    }

    @Override
    public StreamingResponseHandling onRawPartialResponse(Consumer<ParsedAndRawResponse<Response>> handler) {
        StreamingRequestExecutor<Response> executor =
                new StreamingRequestExecutor<>(httpClient, streamingHttpRequest, responseClass);
        return executor.onPartialResponse(handler);
    }
}
