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

import java.util.concurrent.Flow;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

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
    public void execute(HttpRequest request, ServerSentEventListener delegateListener) {

        if (logRequests) {
            HttpRequestLogger.log(log, request);
        }

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

    @Override
    public Flow.Publisher<StreamingHttpEvent> executeWithPublisher(HttpRequest request) {

        Flow.Publisher<StreamingHttpEvent> upstream = delegateHttpClient.executeWithPublisher(request);

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
