package dev.langchain4j.http;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.streaming.ServerSentEventListener;
import dev.langchain4j.http.streaming.StreamingStrategy;

@Experimental
public interface HttpClient {
// TODO package/module: move to lc4j-http and keep HttpException in lc4j-core?

    /**
     * TODO
     *
     * @param httpRequest TODO
     * @return TODO
     * @throws HttpException    TODO
     * @throws RuntimeException TODO
     */
    SuccessfulHttpResponse execute(HttpRequest httpRequest) throws HttpException, RuntimeException;

    /**
     * TODO
     * TODO errors
     * TODO threads
     *
     * @param httpRequest
     * @param listener
     */
    void execute(HttpRequest httpRequest, StreamingStrategy strategy, ServerSentEventListener listener); // TODO name: stream?
}
