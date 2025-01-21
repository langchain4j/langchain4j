package dev.langchain4j.http.streaming;

import dev.langchain4j.Experimental;

import java.io.InputStream;

/**
 * TODO
 * @see ServerSentEventStrategy
 * @see NdJsonStrategy
 */
@Experimental
public interface StreamingStrategy {
// TODO name, package

    // TODO name
    void process(InputStream inputStream, ServerSentEventListener listener);
}
