package dev.langchain4j.http.client.sse;

import dev.langchain4j.Experimental;

/**
 * Handle that can be used to cancel the parsing of server-sent events.
 *
 * @since 1.8.0
 */
@Experimental
public interface ServerSentEventParsingHandle {

    /**
     * Cancels the parsing of server-sent events.
     */
    void cancel();

    /**
     * Returns {@code true} if parsing was cancelled by calling {@link #cancel()}.
     */
    boolean isCancelled();
}
