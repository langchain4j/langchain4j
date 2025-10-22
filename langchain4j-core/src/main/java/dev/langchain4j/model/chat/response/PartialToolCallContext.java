package dev.langchain4j.model.chat.response;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class PartialToolCallContext {

    private final StreamingHandle streamingHandle;

    public PartialToolCallContext(StreamingHandle streamingHandle) {
        this.streamingHandle = ensureNotNull(streamingHandle, "streamingHandle");
    }

    public StreamingHandle streamingHandle() {
        return streamingHandle;
    }
}
