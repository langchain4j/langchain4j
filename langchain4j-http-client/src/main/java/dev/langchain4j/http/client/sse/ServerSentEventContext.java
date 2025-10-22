package dev.langchain4j.http.client.sse;

import dev.langchain4j.model.chat.response.StreamingHandle;

public class ServerSentEventContext {

    private final StreamingHandle streamingHandle;

    public ServerSentEventContext(StreamingHandle streamingHandle) {
        this.streamingHandle = streamingHandle;
    }

    public StreamingHandle streamingHandle() {
        return streamingHandle;
    }
}
