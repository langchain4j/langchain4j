package dev.langchain4j.data.message;


import dev.langchain4j.rag.query.Metadata;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents a request for augmentation.
 */
@Getter
@Builder
public class AugmentationRequest {
    /**
     * The user message to be augmented.
     */
    private final UserMessage userMessage;

    /**
     * Additional metadata related to the augmentation request.
     */
    private final Metadata metadata;
}
