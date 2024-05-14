package dev.langchain4j.http;

import dev.langchain4j.Experimental;

// TODO name
// TODO package/module
@Experimental
public interface HttpClient {

    HttpResponse execute(HttpRequest httpRequest);

    void execute(HttpRequest httpRequest, ServerSentEventListener listener);
}
