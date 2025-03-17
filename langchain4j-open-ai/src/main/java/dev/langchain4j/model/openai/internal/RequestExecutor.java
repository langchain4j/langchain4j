package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;

import java.util.function.Consumer;

class RequestExecutor<Response> implements SyncOrAsyncOrStreaming<Response> {

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final HttpRequest streamingHttpRequest;
    private final Class<Response> responseClass;

    RequestExecutor(HttpClient httpClient,
                    HttpRequest httpRequest,
                    Class<Response> responseClass
    ) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.streamingHttpRequest = null;
        this.responseClass = responseClass;
    }

    RequestExecutor(HttpClient httpClient,
                    HttpRequest httpRequest,
                    HttpRequest streamingHttpRequest,
                    Class<Response> responseClass
    ) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.streamingHttpRequest = streamingHttpRequest;
        this.responseClass = responseClass;
    }

    @Override
    public Response execute() {
        SyncRequestExecutor<Response> executor = new SyncRequestExecutor<>(httpClient, httpRequest, responseClass);
        return executor.execute();
    }

    @Override
    public AsyncResponseHandling onResponse(Consumer<Response> responseHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StreamingResponseHandling onPartialResponse(Consumer<Response> partialResponseHandler) {
        StreamingRequestExecutor<Response> executor =
                new StreamingRequestExecutor<>(httpClient, streamingHttpRequest, responseClass);
        return executor.onPartialResponse(partialResponseHandler);
    }
}
