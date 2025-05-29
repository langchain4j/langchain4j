package dev.langchain4j.rag;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.rag.content.Content;

/**
 * Augments the provided {@link ChatMessage} with retrieved {@link Content}s.
 * <br>
 * This serves as an entry point into the RAG flow in LangChain4j.
 * <br>
 * You are free to use the default implementation ({@link DefaultRetrievalAugmentor}) or to implement a custom one.
 *
 * @see DefaultRetrievalAugmentor
 */
public interface RetrievalAugmentor {

    /**
     * Augments the {@link ChatMessage} provided in the {@link AugmentationRequest} with retrieved {@link Content}s.
     *
     * @param augmentationRequest The {@code AugmentationRequest} containing the {@code ChatMessage} to augment.
     * @return The {@link AugmentationResult} containing the augmented {@code ChatMessage}.
     */
    AugmentationResult augment(AugmentationRequest augmentationRequest);
}
