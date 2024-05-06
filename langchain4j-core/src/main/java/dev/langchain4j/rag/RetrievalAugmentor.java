package dev.langchain4j.rag;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AugmentationRequest;
import dev.langchain4j.data.message.AugmentationResult;
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
public interface RetrievalAugmentor {

    /**
     * Augments the provided {@link UserMessage} with retrieved content.
     *
     * @param userMessage The {@link UserMessage} to be augmented.
     * @param metadata    The {@link Metadata} that may be useful or necessary for retrieval and augmentation.
     * @return The augmented {@link UserMessage}.
     * @deprecated This method is deprecated. Use {@link #augment(AugmentationRequest)} instead.
     */
    @Deprecated
    UserMessage augment(UserMessage userMessage, Metadata metadata);

    /**
     * Augments the provided {@link AugmentationRequest} with retrieved content.
     *
     * @param augmentationRequest The {@link AugmentationRequest} containing the user message and metadata.
     * @return The {@link AugmentationResult} containing the augmented user message.
     */
    default AugmentationResult augment(AugmentationRequest augmentationRequest) { // new API
        UserMessage augmented = augment(augmentationRequest.getUserMessage(), augmentationRequest.getMetadata());
        return AugmentationResult.builder()
                .augmentedUserMessage(augmented)
                .build();
    }
}
