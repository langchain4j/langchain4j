package dev.langchain4j.model.chat.response;

/**
 * @since 1.8.0
 */
public interface StreamingHandle { // TODO name, location

    /**
     * Attempts to cancel the streaming. Idempotent. TODO
     */
    void cancel();

    /**
     * Returns {@code true} if streaming was cancelled by calling {@link #cancel()}.
     */
    boolean isCancelled();
}
