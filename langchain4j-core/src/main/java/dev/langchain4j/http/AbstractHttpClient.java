package dev.langchain4j.http;

public abstract class AbstractHttpClient implements HttpClient {

    public HttpResponse execute(HttpRequest httpRequest) {

        if (logRequests()) {
            // TODO it logs now as dev.langchain4j.http.HttpRequestLogger.log(), not model-specific
            HttpRequestLogger.log(httpRequest);
        }

        HttpResponse httpResponse = doExecute(httpRequest);

        if (logResponses()) {
            // TODO it logs now as dev.langchain4j.http.HttpResponseLogger.log(), not model-specific
            HttpResponseLogger.log(httpResponse);
        }

        return httpResponse;
    }

    protected abstract HttpResponse doExecute(HttpRequest httpRequest);

    public void execute(HttpRequest httpRequest, ServerSentEventListener listener) {

        if (logRequests()) {
            HttpRequestLogger.log(httpRequest);
        }

        doExecute(httpRequest, new ServerSentEventListener() {

            @Override
            public void onStart(HttpResponse httpResponse) {
                if (logResponses()) {
                    HttpResponseLogger.log(httpResponse);
                }
                listener.onStart(httpResponse);
            }

            @Override
            public void onEvent(ServerSentEvent event) {
                // TODO log?
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

    protected abstract boolean logRequests();

    protected abstract boolean logResponses();
}
