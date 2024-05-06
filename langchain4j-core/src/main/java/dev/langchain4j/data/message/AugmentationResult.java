package dev.langchain4j.data.message;

import dev.langchain4j.rag.content.Content;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Represents the result of an augmentation process.
 */
@Getter
@Builder
public class AugmentationResult {
    /**
     * The augmented user message after processing.
     */
    private final UserMessage augmentedUserMessage;

    /**
     * The list of contents retrieved during augmentation.
     */
    private final List<Content> retrievedContents;
}
