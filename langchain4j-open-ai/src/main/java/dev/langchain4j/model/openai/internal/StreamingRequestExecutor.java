package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.util.function.Consumer;

class StreamingRequestExecutor<Response> {

    private final HttpClient httpClient;
    private final HttpRequest streamingHttpRequest;
    private final Class<Response> responseClass;

    StreamingRequestExecutor(HttpClient httpClient, HttpRequest streamingHttpRequest, Class<Response> responseClass) {
        this.httpClient = httpClient;
        this.streamingHttpRequest = streamingHttpRequest;
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
                                return stream(partialResponseHandler, streamingCompletionCallback, errorHandler);
                            }
                        };
                    }

                    @Override
                    public ErrorHandling ignoreErrors() {
                        return new ErrorHandling() {

                            @Override
                            public ResponseHandle execute() {
                                return stream(partialResponseHandler, streamingCompletionCallback, (e) -> {
                                    // intentionally ignoring because user called ignoreErrors()
                                });
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
                                errorHandler);
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
                                });
                    }
                };
            }
        };
    }

    private ResponseHandle stream(
            Consumer<Response> partialResponseHandler,
            Runnable streamingCompletionCallback,
            Consumer<Throwable> errorHandler) {

        ServerSentEventListener listener = new ServerSentEventListener() {

            @Override
            public void onEvent(ServerSentEvent event) {

                if ("[DONE]".equals(event.data())) {
                    return;
                }
                try {
                    if ("error".equals(event.event())) {
                        errorHandler.accept(new RuntimeException(event.data()));
                        return;
                    }
                    Response response = Json.fromJson(event.data(), responseClass);
                    if (response != null) {
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
                errorHandler.accept(t);
            }
        };

        httpClient.execute(streamingHttpRequest, listener);

        return new ResponseHandle();
    }
}
