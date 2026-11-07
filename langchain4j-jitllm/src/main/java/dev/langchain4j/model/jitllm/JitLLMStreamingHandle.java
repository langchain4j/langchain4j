package dev.langchain4j.model.jitllm;

import dev.langchain4j.model.chat.response.StreamingHandle;
import org.beehive.jitllm.api.CancellationToken;

final class JitLLMStreamingHandle implements StreamingHandle {

    private final CancellationToken cancellationToken = new CancellationToken();

    @Override
    public void cancel() {
        cancellationToken.cancel();
    }

    @Override
    public boolean isCancelled() {
        return cancellationToken.isCancelled();
    }

    CancellationToken cancellationToken() {
        return cancellationToken;
    }
}
