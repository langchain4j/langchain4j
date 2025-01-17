package dev.langchain4j.http;

import dev.langchain4j.Experimental;

// TODO name
// TODO package/module
@Experimental
public interface HttpClient {

    /**
     * TODO
     *
     * @param httpRequest
     * @return
     * @throws HttpException TODO good idea?
     */
    HttpResponse execute(HttpRequest httpRequest) throws HttpException; // TODO list of exceptions

    // TODO generic timeout exception? what else?

    /**
     * TODO
     * TODO errors
     * TODO threads
     *
     * @param httpRequest
     * @param listener
     */
    void execute(HttpRequest httpRequest, ServerSentEventListener listener); // TODO name stream?
}
