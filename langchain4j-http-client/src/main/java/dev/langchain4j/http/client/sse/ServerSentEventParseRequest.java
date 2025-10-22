package dev.langchain4j.http.client.sse;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.io.InputStream;

/**
 * @since 1.8.0
 */
public class ServerSentEventParseRequest {

    private final InputStream inputStream;
    private final ServerSentEventListener listener;
    private final ServerSentEventParsingHandle parsingHandle;

    public ServerSentEventParseRequest(Builder builder) {
        this.inputStream = ensureNotNull(builder.inputStream, "inputStream");
        this.listener = ensureNotNull(builder.listener, "listener");
        this.parsingHandle = ensureNotNull(builder.parsingHandle, "parsingHandle");
    }

    public InputStream inputStream() {
        return inputStream;
    }

    public ServerSentEventListener listener() {
        return listener;
    }

    public ServerSentEventParsingHandle parsingHandle() {
        return parsingHandle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private InputStream inputStream;
        private ServerSentEventListener listener;
        private ServerSentEventParsingHandle parsingHandle;

        public Builder inputStream(InputStream inputStream) {
            this.inputStream = inputStream;
            return this;
        }

        public Builder listener(ServerSentEventListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder parsingHandle(ServerSentEventParsingHandle parsingHandle) {
            this.parsingHandle = parsingHandle;
            return this;
        }

        public ServerSentEventParseRequest build() {
            return new ServerSentEventParseRequest(this);
        }
    }
}
