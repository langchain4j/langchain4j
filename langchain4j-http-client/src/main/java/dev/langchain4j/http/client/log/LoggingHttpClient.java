package dev.langchain4j.http.client.log;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * An {@link HttpClient} decorator that logs requests and responses.
 * <p>
 * <b>Streaming response logging is blocking IO on the delivery thread.</b> For the streaming paths
 * ({@link #execute(HttpRequest, ServerSentEventListener)},
 * {@link #execute(HttpRequest, ServerSentEventParser, ServerSentEventListener)} and especially
 * {@link #stream(HttpRequest, ServerSentEventParser)}), each server-sent event is logged
 * synchronously on the thread that delivers it — for the publisher path this is the underlying HTTP
 * client's non-blocking worker thread. Whether that log call actually blocks is decided by the logging
 * backend's appender, which this client does not control: a <em>synchronous</em> appender (e.g. a plain
 * file/console appender) performs a blocking write on the worker thread, which under load can stall the
 * worker and collapse streaming throughput. Therefore, <b>when {@code logResponses} is enabled for
 * streaming, configure an asynchronous appender</b> (e.g. Logback {@code AsyncAppender}, Log4j2 async
 * loggers, tinylog {@code writingthread=true}) so log writes happen off the delivery thread.
 */
@Internal
public class LoggingHttpClient implements HttpClient {

    private static final Logger DEFAULT_LOG = LoggerFactory.getLogger(LoggingHttpClient.class);

    private final HttpClient delegateHttpClient;
    private final boolean logRequests;
    private final boolean logResponses;
    private final Logger log;

    public LoggingHttpClient(HttpClient delegateHttpClient, Boolean logRequests, Boolean logResponses) {
        this.delegateHttpClient = ensureNotNull(delegateHttpClient, "delegateHttpClient");
        this.logRequests = getOrDefault(logRequests, false);
        this.logResponses = getOrDefault(logResponses, false);
        this.log = DEFAULT_LOG;
    }

    public LoggingHttpClient(HttpClient delegateHttpClient, Boolean logRequests, Boolean logResponses, Logger logger) {
        this.delegateHttpClient = ensureNotNull(delegateHttpClient, "delegateHttpClient");
        this.logRequests = getOrDefault(logRequests, false);
        this.logResponses = getOrDefault(logResponses, false);
        this.log = getOrDefault(logger, DEFAULT_LOG);
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {

        if (logRequests) {
            HttpRequestLogger.log(log, request);
        }

        SuccessfulHttpResponse response = delegateHttpClient.execute(request);

        if (logResponses) {
            HttpResponseLogger.log(log, response);
        }

        return response;
    }

    @Override
    public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {

        if (logRequests) {
            HttpRequestLogger.log(log, request);
        }

        CompletableFuture<SuccessfulHttpResponse> future =
                delegateHttpClient.executeAsync(request);

        if (!logResponses) {
            return future;
        }

        return future.whenComplete((response, throwable) -> {
            if (response != null) {
                HttpResponseLogger.log(log, response);
            }
        });
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventListener delegateListener) {

        if (logRequests) {
            HttpRequestLogger.log(log, request);
        }

        // TODO for logRequests = false do not wrap the delegateListener?

        this.delegateHttpClient.execute(request, new ServerSentEventListener() {

            @Override
            public void onOpen(SuccessfulHttpResponse response) {
                if (logResponses) {
                    HttpResponseLogger.log(log, response);
                }
                delegateListener.onOpen(response);
            }

            @Override
            public void onEvent(ServerSentEvent event) {
                if (logResponses) {
                    log.debug("{}", event);
                }
                delegateListener.onEvent(event);
            }

            @Override
            public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                if (logResponses) {
                    log.debug("{}", event);
                }
                delegateListener.onEvent(event, context);
            }

            @Override
            public void onError(Throwable throwable) {
                delegateListener.onError(throwable);
            }

            @Override
            public void onClose() {
                delegateListener.onClose();
            }
        });
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener delegateListener) {

        if (logRequests) {
            HttpRequestLogger.log(log, request);
        }

        // TODO for logRequests = false do not wrap the delegateListener?

        this.delegateHttpClient.execute(request, parser, new ServerSentEventListener() {

            @Override
            public void onOpen(SuccessfulHttpResponse response) {
                if (logResponses) {
                    HttpResponseLogger.log(log, response);
                }
                delegateListener.onOpen(response);
            }

            @Override
            public void onEvent(ServerSentEvent event) {
                if (logResponses) {
                    log.debug("{}", event);
                }
                delegateListener.onEvent(event);
            }

            @Override
            public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
                if (logResponses) {
                    log.debug("{}", event);
                }
                delegateListener.onEvent(event, context);
            }

            @Override
            public void onError(Throwable throwable) {
                delegateListener.onError(throwable);
            }

            @Override
            public void onClose() {
                delegateListener.onClose();
            }
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * When {@code logResponses} is enabled, each event is logged in {@code onNext}, i.e. synchronously on
     * the upstream's delivery (worker) thread. See the class-level note: use an asynchronous appender so
     * this logging does not perform blocking IO on that non-blocking thread.
     */
    @Override
    public Flow.Publisher<StreamingHttpEvent> stream(HttpRequest request, ServerSentEventParser parser) {

        Flow.Publisher<StreamingHttpEvent> upstream = delegateHttpClient.stream(request, parser);

        return new Flow.Publisher<StreamingHttpEvent>() {

            @Override
            public void subscribe(Flow.Subscriber<? super StreamingHttpEvent> downstream) {

                if (logRequests) {
                    HttpRequestLogger.log(log, request);
                }

                if (!logResponses) {
                    upstream.subscribe(downstream);
                    return;
                }

                upstream.subscribe(new Flow.Subscriber<>() {

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        downstream.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(StreamingHttpEvent event) {
                        if (event instanceof SuccessfulHttpResponse response) {
                            HttpResponseLogger.log(log, response);
                        } else if (event instanceof ServerSentEvent sse) {
                            log.debug("{}", sse);
                        }
                        downstream.onNext(event);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        downstream.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        downstream.onComplete();
                    }
                });
            }
        };
    }
}
