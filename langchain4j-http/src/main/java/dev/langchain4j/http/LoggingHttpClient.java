package dev.langchain4j.http;

import dev.langchain4j.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.internal.Utils.getOrDefault;

@Experimental
public abstract class LoggingHttpClient implements HttpClient {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final boolean logRequests;
    private final boolean logResponses;

    protected LoggingHttpClient(Boolean logRequests, Boolean logResponses) {
        this.logRequests = getOrDefault(logRequests, false);
        this.logResponses = getOrDefault(logResponses, false);
    }

    public HttpResponse execute(HttpRequest httpRequest) {

        if (logRequests) {
            HttpRequestLogger.log(log, httpRequest);
        }

        HttpResponse httpResponse = doExecute(httpRequest);

        if (logResponses) {
            HttpResponseLogger.log(log, httpResponse);
        }

        return httpResponse;
    }

    protected abstract HttpResponse doExecute(HttpRequest httpRequest);

    public void execute(HttpRequest httpRequest, ServerSentEventListener listener) {

        if (logRequests) {
            HttpRequestLogger.log(log, httpRequest);
        }

        doExecute(httpRequest, new ServerSentEventListener() {

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

    protected abstract void doExecute(HttpRequest httpRequest, ServerSentEventListener listener);
}
