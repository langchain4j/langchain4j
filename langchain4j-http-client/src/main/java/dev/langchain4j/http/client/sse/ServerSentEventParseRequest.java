package dev.langchain4j.http.client.sse;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.io.InputStream;
import dev.langchain4j.model.chat.response.StreamingHandle;

/**
 * @since 1.8.0
 */
public class ServerSentEventParseRequest {

    private final InputStream inputStream;
    private final ServerSentEventListener listener;
    private final StreamingHandle streamingHandle;

    public ServerSentEventParseRequest(Builder builder) {
        this.inputStream = ensureNotNull(builder.inputStream, "inputStream");
        this.listener = ensureNotNull(builder.listener, "listener");
        this.streamingHandle = ensureNotNull(builder.streamingHandle, "streamingHandle");
    }

    public InputStream inputStream() {
        return inputStream;
    }

    public ServerSentEventListener listener() {
        return listener;
    }

    public StreamingHandle streamingHandle() {
        return streamingHandle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private InputStream inputStream;
        private ServerSentEventListener listener;
        private StreamingHandle streamingHandle;

        public Builder inputStream(InputStream inputStream) {
            this.inputStream = inputStream;
            return this;
        }

        public Builder listener(ServerSentEventListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder streamingHandle(StreamingHandle streamingHandle) {
            this.streamingHandle = streamingHandle;
            return this;
        }

        public ServerSentEventParseRequest build() {
            return new ServerSentEventParseRequest(this);
        }
    }
}
