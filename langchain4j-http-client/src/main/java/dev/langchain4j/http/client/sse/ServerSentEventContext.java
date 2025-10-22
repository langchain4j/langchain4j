package dev.langchain4j.http.client.sse;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * @since 1.8.0 // TODO check all new classes and methods
 */
public class ServerSentEventContext {

    private final ServerSentEventParsingHandle parsingHandle;

    public ServerSentEventContext(ServerSentEventParsingHandle parsingHandle) {
        this.parsingHandle = ensureNotNull(parsingHandle, "parsingHandle");
    }

    public ServerSentEventParsingHandle parsingHandle() {
        return parsingHandle;
    }
}
