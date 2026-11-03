package dev.langchain4j.model.chat;

import dev.langchain4j.exception.UnsupportedFeatureException;

/**
 * Thrown by the default {@code doChat} methods of {@link ChatModel} and {@link StreamingChatModel}. It remembers the
 * model whose default method threw it, so that only that model falls back to its own {@code chat} method, and not a
 * model whose working {@code doChat} calls another model that is not implemented.
 */
final class DoChatNotImplementedException extends UnsupportedFeatureException {

    private final transient Object model;

    DoChatNotImplementedException(String message, Object model) {
        super(message);
        this.model = model;
    }

    boolean isThrownBy(Object model) {
        return this.model == model;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
