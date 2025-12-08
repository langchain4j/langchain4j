package dev.langchain4j.model.openai.internal;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.CancellationUnsupportedHandle;
import dev.langchain4j.model.chat.response.StreamingHandle;

import java.util.function.Consumer;

import static dev.langchain4j.http.client.sse.ServerSentEventParsingHandleUtils.toStreamingHandle;

class StreamingRequestExecutor<Response> {

    private final HttpClient httpClient;
    private final HttpRequest streamingHttpRequest;
    private final Class<Response> responseClass;

    StreamingRequestExecutor(HttpClient httpClient, HttpRequest streamingHttpRequest, Class<Response> responseClass) {
        this.httpClient = httpClient;
        this.streamingHttpRequest = streamingHttpRequest;
        this.responseClass = responseClass;
    }

    StreamingResponseHandling onPartialResponse(Consumer<ParsedAndRawResponse<Response>> partialResponseHandler) {

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
            Consumer<ParsedAndRawResponse<Response>> partialResponseHandler,
            Runnable streamingCompletionCallback,
            Consumer<Throwable> errorHandler) {

        ServerSentEventListener listener = new ServerSentEventListener() {

            volatile SuccessfulHttpResponse response;
            volatile StreamingHandle streamingHandle;

            @Override
            public void onOpen(SuccessfulHttpResponse response) {
                this.response = response;
            }

            @Override
            public void onEvent(ServerSentEvent event) {
                onEvent(event, new ServerSentEventContext(new CancellationUnsupportedHandle()));
            }

            @Override
            public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                if (streamingHandle == null) {
                    streamingHandle = toStreamingHandle(context.parsingHandle());
                }

                if ("[DONE]".equals(event.data())) {
                    return;
                }
                try {
                    if ("error".equals(event.event())) {
                        errorHandler.accept(new RuntimeException(event.data()));
                        return;
                    }
                    Response parsedResponse = Json.fromJson(event.data(), responseClass);
                    if (parsedResponse != null) {
                        ParsedAndRawResponse parsedAndRawResponse = ParsedAndRawResponse.builder()
                                .parsedResponse(parsedResponse)
                                .rawHttpResponse(response)
                                .rawServerSentEvent(event)
                                .streamingHandle(streamingHandle)
                                .build();
                        partialResponseHandler.accept(parsedAndRawResponse); // do not handle exception, fail-fast
                    }
                } catch (Exception e) {
                    errorHandler.accept(e);
                }
            }

            @Override
            public void onClose() {
                if (streamingHandle == null || !streamingHandle.isCancelled()) {
                    streamingCompletionCallback.run();
                }
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
