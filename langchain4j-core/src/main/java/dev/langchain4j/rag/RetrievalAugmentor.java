package dev.langchain4j.rag;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;

import static dev.langchain4j.internal.Exceptions.runtime;

/**
 * Augments the provided {@link ChatMessage} with retrieved {@link Content}s.
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
     * Augments the {@link ChatMessage} provided in the {@link AugmentationRequest} with retrieved {@link Content}s.
     * <br>
     * This method has a default implementation in order to <b>temporarily</b> support
     * current custom implementations of {@code RetrievalAugmentor}. The default implementation will be removed soon.
     *
     * @param augmentationRequest The {@code AugmentationRequest} containing the {@code ChatMessage} to augment.
     * @return The {@link AugmentationResult} containing the augmented {@code ChatMessage}.
     */
    default AugmentationResult augment(AugmentationRequest augmentationRequest) {

        if (!(augmentationRequest.chatMessage() instanceof UserMessage)) {
            throw runtime("Please implement 'AugmentationResult augment(AugmentationRequest)' method " +
                    "in order to augment " + augmentationRequest.chatMessage().getClass());
        }

        UserMessage augmented = augment((UserMessage) augmentationRequest.chatMessage(), augmentationRequest.metadata());

        return AugmentationResult.builder()
                .chatMessage(augmented)
                .build();
    }

    /**
     * Augments the provided {@link UserMessage} with retrieved content.
     *
     * @param userMessage The {@link UserMessage} to be augmented.
     * @param metadata    The {@link Metadata} that may be useful or necessary for retrieval and augmentation.
     * @return The augmented {@link UserMessage}.
     * @deprecated Use/implement {@link #augment(AugmentationRequest)} instead.
     */
    @Deprecated
    UserMessage augment(UserMessage userMessage, Metadata metadata);
}
