package dev.langchain4j.http.client.sse;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;

/**
 * @since 1.8.0
 */
@Experimental
public class ServerSentEventContext {

    private final ServerSentEventParsingHandle parsingHandle;

    public ServerSentEventContext(ServerSentEventParsingHandle parsingHandle) {
        this.parsingHandle = ensureNotNull(parsingHandle, "parsingHandle");
    }

    public ServerSentEventParsingHandle parsingHandle() {
        return parsingHandle;
    }
}
