package dev.langchain4j.http.log;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpRequest;
import dev.langchain4j.http.HttpResponse;
import dev.langchain4j.http.ServerSentEvent;
import dev.langchain4j.http.ServerSentEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public class LoggingHttpClient implements HttpClient {

    private final HttpClient delegate;
    private final boolean logRequests;
    private final boolean logResponses;
    private final Logger log;

    public LoggingHttpClient(HttpClient delegate, Boolean logRequests, Boolean logResponses) {
        this.delegate = ensureNotNull(delegate, "delegate");
        this.logRequests = getOrDefault(logRequests, false);
        this.logResponses = getOrDefault(logResponses, false);
        if (this.logRequests || this.logResponses) {
            this.log = LoggerFactory.getLogger(delegate.getClass()); // TODO or static for LoggingHttpClient.class?
        } else {
            this.log = null;
        }
    }

    public HttpResponse execute(HttpRequest httpRequest) {

        if (logRequests) {
            HttpRequestLogger.log(log, httpRequest);
        }

        HttpResponse httpResponse = delegate.execute(httpRequest);

        if (logResponses) {
            HttpResponseLogger.log(log, httpResponse);
        }

        return httpResponse;
    }

    public void execute(HttpRequest httpRequest, ServerSentEventListener listener) {

        if (logRequests) {
            HttpRequestLogger.log(log, httpRequest); // TODO log on the thread where request is actually made?
        }

        delegate.execute(httpRequest, new ServerSentEventListener() {

            @Override
            public void onStart(HttpResponse httpResponse) {
                if (logResponses) {
                    HttpResponseLogger.log(log, httpResponse);
                }
                listener.onStart(httpResponse);
            }

            @Override
            public void onEvent(ServerSentEvent event) {
                if (logResponses) {
                    log.debug("{}", event); // TODO?
                }
                listener.onEvent(event);
            }

            @Override
            public void onError(Throwable throwable) {
                // TODO log?
                listener.onError(throwable);
            }
        });
    }
}
