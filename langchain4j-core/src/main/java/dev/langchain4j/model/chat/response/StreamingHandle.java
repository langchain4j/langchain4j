package dev.langchain4j.model.chat.response;

/**
 * @since 1.8.0
 */
public interface StreamingHandle { // TODO name, location

    // TODO name
    void cancel(); // TODO return/throw exception?

    boolean isCancelled(); // TODO name
}
