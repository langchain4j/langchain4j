package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;

import java.util.function.Consumer;
import java.util.function.Supplier;

class StreamingRequestExecutor<Request, Response> {

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    private final Supplier<Request> requestWithStreamSupplier;
    private final Class<Response> responseClass;

    StreamingRequestExecutor(
            HttpClient httpClient,
            HttpRequest httpRequest,
            Supplier<Request> requestWithStreamSupplier,
            Class<Response> responseClass
    ) {
        this.httpClient = httpClient;
        this.httpRequest = httpRequest;
        this.requestWithStreamSupplier = requestWithStreamSupplier;
        this.responseClass = responseClass;
    }

    StreamingResponseHandling onPartialResponse(Consumer<Response> partialResponseHandler) {

        return new StreamingResponseHandling() {

            @Override
            public StreamingCompletionHandling onComplete(Runnable streamingCompletionCallback) {
                return new StreamingCompletionHandling() {

                    @Override
                    public ErrorHandling onError(Consumer<Throwable> errorHandler) {
                        return new ErrorHandling() {

                            @Override
                            public ResponseHandle execute() {
                                return stream(
                                        partialResponseHandler,
                                        streamingCompletionCallback,
                                        errorHandler
                                );
                            }
                        };
                    }

                    @Override
                    public ErrorHandling ignoreErrors() {
                        return new ErrorHandling() {

                            @Override
                            public ResponseHandle execute() {
                                return stream(
                                        partialResponseHandler,
                                        streamingCompletionCallback,
                                        (e) -> {
                                            // intentionally ignoring because user called ignoreErrors()
                                        }
                                );
                            }
                        };
                    }
                };
            }

            @Override
            public ErrorHandling onError(Consumer<Throwable> errorHandler) {
                return new ErrorHandling() {

                    @Override
                    public ResponseHandle execute() {
                        return stream(
                                partialResponseHandler,
                                () -> {
                                    // intentionally ignoring because user did not provide callback
                                },
                                errorHandler
                        );
                    }
                };
            }

            @Override
            public ErrorHandling ignoreErrors() {
                return new ErrorHandling() {

                    @Override
                    public ResponseHandle execute() {
                        return stream(
                                partialResponseHandler,
                                () -> {
                                    // intentionally ignoring because user did not provide callback
                                },
                                (e) -> {
                                    // intentionally ignoring because user called ignoreErrors()
                                }
                        );
                    }
                };
            }
        };
    }

    private ResponseHandle stream(
            Consumer<Response> partialResponseHandler,
            Runnable streamingCompletionCallback,
            Consumer<Throwable> errorHandler
    ) {

        // TODO is it really required?
        Request streamingRequest = requestWithStreamSupplier.get();

        HttpRequest streamingHttpRequest = HttpRequest.builder()
                .method(httpRequest.method())
                .url(httpRequest.url())
                .headers(httpRequest.headers())
                .body(Json.toJson(streamingRequest))
                .build();

        ServerSentEventListener listener = new ServerSentEventListener() {

            @Override
            public void onEvent(ServerSentEvent event) {

                if ("[DONE]".equals(event.data())) {
                    return;
                }

                try {
                    Response response = Json.fromJson(event.data(), responseClass);
                    if (response != null) {
                        // TODO?
                        partialResponseHandler.accept(response); // do not handle exception, fail-fast
                    }
                } catch (Exception e) {
                    errorHandler.accept(e);
                }
            }

            @Override
            public void onClose() {
                streamingCompletionCallback.run();
            }

            @Override
            public void onError(Throwable t) {

                // TODO remove this when migrating from okhttp
                if (t instanceof IllegalArgumentException && "byteCount < 0: -1".equals(t.getMessage())) {
                    streamingCompletionCallback.run();
                    return;
                }

                errorHandler.accept(t);
            }
        };

        httpClient.execute(streamingHttpRequest, listener);

        return new ResponseHandle(); // TODO
    }
}
