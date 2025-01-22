package dev.langchain4j.http.client.log;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpException;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public class LoggingHttpClient implements HttpClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingHttpClient.class);

    private final HttpClient delegate;
    private final boolean logRequests;
    private final boolean logResponses;

    public LoggingHttpClient(HttpClient delegate, Boolean logRequests, Boolean logResponses) {
        this.delegate = ensureNotNull(delegate, "delegate");
        this.logRequests = getOrDefault(logRequests, false);
        this.logResponses = getOrDefault(logResponses, false);
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {

        if (logRequests) {
            HttpRequestLogger.log(log, request);
        }

        SuccessfulHttpResponse response = delegate.execute(request);

        if (logResponses) {
            HttpResponseLogger.log(log, response);
        }

        return response;
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {

        if (logRequests) {
            HttpRequestLogger.log(log, request); // TODO log on the thread where request is actually made?
        }

        delegate.execute(request, parser, new ServerSentEventListener() {

            @Override
            public void onOpen(SuccessfulHttpResponse response) {
                if (logResponses) {
                    HttpResponseLogger.log(log, response);
                }
                listener.onOpen(response);
            }

            @Override
            public void onEvent(ServerSentEvent event) {
                if (logResponses) {
                    log.debug("{}", event);
                }
                listener.onEvent(event);
            }

            @Override
            public void onError(Throwable throwable) {
                listener.onError(throwable);
            }
        });
    }
}
