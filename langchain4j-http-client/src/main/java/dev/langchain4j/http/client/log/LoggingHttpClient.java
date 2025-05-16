package dev.langchain4j.http.client.log;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
public class LoggingHttpClient implements HttpClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingHttpClient.class);

    private final HttpClient delegateHttpClient;
    private final boolean logRequests;
    private final boolean logResponses;

    public LoggingHttpClient(HttpClient delegateHttpClient, Boolean logRequests, Boolean logResponses) {
        this.delegateHttpClient = ensureNotNull(delegateHttpClient, "delegateHttpClient");
        this.logRequests = getOrDefault(logRequests, false);
        this.logResponses = getOrDefault(logResponses, false);
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
            public void onError(Throwable throwable) {
                delegateListener.onError(throwable);
            }

            @Override
            public void onClose() {
                delegateListener.onClose();
            }
        });
    }
}
