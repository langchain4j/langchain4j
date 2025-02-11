package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;

import java.util.function.Consumer;
import java.util.function.Supplier;

class RequestExecutor<Request, Response> implements SyncOrAsyncOrStreaming<Response> {

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final Class<Response> responseClass;

    private final Supplier<Request> requestWithStreamSupplier;

    RequestExecutor(HttpClient httpClient, HttpRequest httpRequest, Class<Response> responseClass) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.requestWithStreamSupplier = null;
        this.responseClass = responseClass;
    }

    RequestExecutor(HttpClient httpClient,
                    HttpRequest httpRequest,
                    Supplier<Request> requestWithStreamSupplier,
                    Class<Response> responseClass
    ) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.requestWithStreamSupplier = requestWithStreamSupplier;
        this.responseClass = responseClass;
    }

    @Override
    public Response execute() {
        return new SyncRequestExecutor<>(httpClient, httpRequest, responseClass).execute();
    }

    @Override
    public AsyncResponseHandling onResponse(Consumer<Response> responseHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StreamingResponseHandling onPartialResponse(Consumer<Response> partialResponseHandler) {
        return new StreamingRequestExecutor<>(httpClient, httpRequest, requestWithStreamSupplier, responseClass)
                .onPartialResponse(partialResponseHandler);
    }
}
