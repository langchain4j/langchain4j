package dev.langchain4j.model.qianfan.client;


import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
public class StreamingRequestExecutor<Request, Response, ResponseContent> {

    private static final Logger log = LoggerFactory.getLogger(StreamingRequestExecutor.class);
    private final OkHttpClient okHttpClient;
    private final String endpointUrl;
    private final Supplier<Request> requestWithStreamSupplier;
    private final Class<Response> responseClass;
    private final Function<Response, ResponseContent> streamEventContentExtractor;
    private final boolean logStreamingResponses;

    StreamingRequestExecutor(OkHttpClient okHttpClient, String endpointUrl, Supplier<Request> requestWithStreamSupplier,
            Class<Response> responseClass, Function<Response, ResponseContent> streamEventContentExtractor,
            boolean logStreamingResponses) {
        this.okHttpClient = okHttpClient;
        this.endpointUrl = endpointUrl;
        this.requestWithStreamSupplier = requestWithStreamSupplier;
        this.responseClass = responseClass;
        this.streamEventContentExtractor = streamEventContentExtractor;
        this.logStreamingResponses = logStreamingResponses;
    }

    StreamingResponseHandling onPartialResponse(final Consumer<ResponseContent> partialResponseHandler) {
        return new StreamingResponseHandling() {
            public StreamingCompletionHandling onComplete(final Runnable streamingCompletionCallback) {
                return new StreamingCompletionHandling() {
                    public ErrorHandling onError(final Consumer<Throwable> errorHandler) {
                        return new ErrorHandling() {
                            public void execute() {
                                StreamingRequestExecutor.this.stream(partialResponseHandler,
                                        streamingCompletionCallback, errorHandler);
                            }
                        };
                    }

                    public ErrorHandling ignoreErrors() {
                        return new ErrorHandling() {
                            public void execute() {
                                StreamingRequestExecutor.this.stream(partialResponseHandler,
                                        streamingCompletionCallback, (e) -> {
                                        });
                            }
                        };
                    }
                };
            }

            public ErrorHandling onError(final Consumer<Throwable> errorHandler) {
                return new ErrorHandling() {
                    public void execute() {
                        StreamingRequestExecutor.this.stream(partialResponseHandler, () -> {
                        }, errorHandler);
                    }
                };
            }

            public ErrorHandling ignoreErrors() {
                return new ErrorHandling() {
                    public void execute() {
                        StreamingRequestExecutor.this.stream(partialResponseHandler, () -> {
                        }, (e) -> {
                        });
                    }
                };
            }
        };
    }

    private void stream(final Consumer<ResponseContent> partialResponseHandler,
            final Runnable streamingCompletionCallback, final Consumer<Throwable> errorHandler) {
        Request request = this.requestWithStreamSupplier.get();
        String requestJson = Json.toJson(request);
        okhttp3.Request okHttpRequest = (new okhttp3.Request.Builder()).url(this.endpointUrl)
                .post(RequestBody.create(requestJson, MediaType.get("application/json; charset=utf-8"))).build();
        EventSourceListener eventSourceListener = new EventSourceListener() {
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                if (StreamingRequestExecutor.this.logStreamingResponses) {
                    ResponseLoggingInterceptor.log(response);
                }

            }

            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if (StreamingRequestExecutor.this.logStreamingResponses) {
                    StreamingRequestExecutor.log.debug("onEvent() {}", data);
                }

                if ("[DONE]".equals(data)) {
                    streamingCompletionCallback.run();
                } else {
                    try {
                        Response response = Json.fromJson(data, StreamingRequestExecutor.this.responseClass);
                        ResponseContent responseContent = StreamingRequestExecutor.this.streamEventContentExtractor.apply(
                                response);
                        if (responseContent != null) {
                            partialResponseHandler.accept(responseContent);
                        }
                    } catch (Exception var7) {
                        errorHandler.accept(var7);
                    }

                }
            }

            public void onClosed(EventSource eventSource) {
                if (StreamingRequestExecutor.this.logStreamingResponses) {
                    StreamingRequestExecutor.log.debug("onClosed()");
                }
                streamingCompletionCallback.run();

            }

            public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                if (StreamingRequestExecutor.this.logStreamingResponses) {

                    StreamingRequestExecutor.log.debug("reqeust url:",response.request().url().toString());
                    StreamingRequestExecutor.log.debug("onFailure()", t);
                    ResponseLoggingInterceptor.log(response);
                }

                if (t != null) {
                    errorHandler.accept(t);
                } else {
                    try {
                        errorHandler.accept(Utils.toException(response));
                    } catch (IOException var5) {
                        errorHandler.accept(var5);
                    }
                }

            }
        };
        EventSources.createFactory(this.okHttpClient).newEventSource(okHttpRequest, eventSourceListener);
    }
}
