package dev.langchain4j.data.message;

import dev.langchain4j.rag.content.Content;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Represents an augmented message containing information about the user message and associated contents.
 */
@Getter
@Builder
public class AugmentedMessage {
    private final UserMessage userMessage; // The augmented user message.
    private final List<Content> contents; // The list of contents used to augment the associated the user message.
}
