package dev.langchain4j.model.watsonx;

import static java.util.Objects.nonNull;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.StreamingHandle;
import java.util.concurrent.CompletableFuture;

@Internal
class WatsonxStreamingHandle implements StreamingHandle {

    private volatile CompletableFuture<?> streamingFuture;
    private volatile boolean isCancelled;

    void bindTo(CompletableFuture<?> streamingFuture) {
        this.streamingFuture = streamingFuture;
        if (isCancelled) cancelStreaming();
    }

    @Override
    public void cancel() {
        isCancelled = true;
        cancelStreaming();
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    private void cancelStreaming() {
        var future = streamingFuture;
        if (nonNull(future)) {
            try {
                future.cancel(true);
            } catch (Exception ignored) {
                // The stream is already over, there is nothing left to cancel.
            }
        }
    }
}
