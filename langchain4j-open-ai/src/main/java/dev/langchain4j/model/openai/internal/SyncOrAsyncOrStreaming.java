package dev.langchain4j.model.openai.internal;

import java.util.function.Consumer;

public interface SyncOrAsyncOrStreaming<ResponseContent> extends SyncOrAsync<ResponseContent> {

    StreamingResponseHandling onPartialResponse(Consumer<ResponseContent> partialResponseHandler);
}
