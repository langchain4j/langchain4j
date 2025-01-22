package dev.langchain4j.http.client.sse;

import dev.langchain4j.Experimental;

import java.io.InputStream;

/**
 * TODO
 *
 * @see DefaultServerSentEventParser
 */
@Experimental
public interface ServerSentEventParser {

    void parse(InputStream httpResponseBody, ServerSentEventListener listener);
}
