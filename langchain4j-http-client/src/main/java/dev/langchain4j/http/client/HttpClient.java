package dev.langchain4j.http.client;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

@Experimental
public interface HttpClient {

    /**
     * TODO
     *
     * @param httpRequest
     * @return
     * @throws HttpException
     * @throws RuntimeException
     */
    SuccessfulHttpResponse execute(HttpRequest httpRequest) throws HttpException, RuntimeException;

    /**
     * TODO
     * TODO DefaultServerSentEventParser
     * TODO errors
     * TODO threads
     *
     * @param httpRequest
     * @param listener
     */
    default void execute(HttpRequest httpRequest, ServerSentEventListener listener) {
        execute(httpRequest, new DefaultServerSentEventParser(), listener);
    }

    /**
     * TODO
     * TODO ServerSentEventParser
     * TODO errors
     * TODO threads
     *
     * @param httpRequest
     * @param parser
     * @param listener
     */
    void execute(HttpRequest httpRequest, ServerSentEventParser parser, ServerSentEventListener listener);

    // TODO close()? AutoCloseable?
}
