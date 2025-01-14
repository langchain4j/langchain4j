package dev.langchain4j.http;

import dev.langchain4j.Experimental;

// TODO name
// TODO package/module
@Experimental
public interface HttpClient extends AutoCloseable {

    // TODO split streaming and non-streaming into separate interfaces?

    /**
     * TODO
     *
     * @param httpRequest
     * @return
     * @throws HttpException TODO good idea?
     */
    HttpResponse execute(HttpRequest httpRequest) throws HttpException; // TODO list of exceptions

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
