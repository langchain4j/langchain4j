package dev.langchain4j.http.client;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

@Experimental
public interface HttpClient {

    /**
     * TODO
     *
     * @param request
     * @return
     * @throws HttpException
     * @throws RuntimeException
     */
    SuccessfulHttpResponse execute(HttpRequest request) throws HttpException, RuntimeException;

    /**
     * TODO
     * TODO DefaultServerSentEventParser
     * TODO errors
     * TODO threads
     *
     * @param request
     * @param listener
     */
    default void execute(HttpRequest request, ServerSentEventListener listener) {
        execute(request, new DefaultServerSentEventParser(), listener);
    }

    /**
     * TODO
     * TODO ServerSentEventParser
     * TODO errors
     * TODO threads
     *
     * @param request
     * @param parser
     * @param listener
     */
    void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener);
}
