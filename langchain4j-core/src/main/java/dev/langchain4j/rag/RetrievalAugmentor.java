package dev.langchain4j.rag;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.query.Metadata;

/**
 * Augments the provided {@link UserMessage} with retrieved content.
 * <br>
 * This serves as an entry point into the RAG flow in LangChain4j.
 * <br>
 * You are free to use the default implementation ({@link DefaultRetrievalAugmentor}) or to implement a custom one.
 *
 * @see DefaultRetrievalAugmentor
 */
@Experimental
public interface RetrievalAugmentor<T> {

    /**
     * Augments the provided {@link UserMessage} with retrieved content.
     *
     * @param userMessage The {@link UserMessage} to be augmented.
     * @param metadata    The {@link Metadata} that may be useful or necessary for retrieval and augmentation.
     * @return The augmented {@link UserMessage}.
     */
    T augment(UserMessage userMessage, Metadata metadata);
}
