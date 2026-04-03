package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.chat.response.StreamingHandle;
import reactor.core.Disposable;

/**
 * @since 1.8.0
 */
class AzureOpenAiStreamingHandle implements StreamingHandle {

    private final Disposable disposable;
    private volatile boolean isCancelled;

    AzureOpenAiStreamingHandle(Disposable disposable) {
        this.disposable = ensureNotNull(disposable, "disposable");
    }

    @Override
    public void cancel() {
        isCancelled = true;
        try {
            disposable.dispose();
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }
}
