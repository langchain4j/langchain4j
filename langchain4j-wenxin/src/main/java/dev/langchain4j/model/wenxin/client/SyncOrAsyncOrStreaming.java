package dev.langchain4j.model.wenxin.client;

import java.util.function.Consumer;

public interface SyncOrAsyncOrStreaming<ResponseContent> extends SyncOrAsync<ResponseContent> {
    StreamingResponseHandling onPartialResponse(Consumer<ResponseContent> var1);
}
