package dev.langchain4j.http.client;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.streaming.ServerSentEventListener;
import dev.langchain4j.http.client.streaming.StreamingStrategy;

@Experimental
public interface HttpClient {

    /**
     * TODO
     *
     * @param httpRequest TODO
     * @return TODO
     * @throws HttpException    TODO
     * @throws RuntimeException TODO
     */
    SuccessfulHttpResponse execute(HttpRequest httpRequest) throws HttpException, RuntimeException;

    // TODO naming: stream/async/event/sse?

    /**
     * TODO
     * TODO errors
     * TODO threads
     *
     * @param httpRequest
     * @param listener
     */
    void execute(HttpRequest httpRequest, StreamingStrategy strategy, ServerSentEventListener listener); // TODO name: stream?

    // TODO close()? AutoCloseable?
}
