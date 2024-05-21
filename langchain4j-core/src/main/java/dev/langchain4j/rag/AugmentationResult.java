package dev.langchain4j.rag;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.rag.content.Content;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents the result of a {@link ChatMessage} augmentation.
 */
public class AugmentationResult {

    /**
     * The augmented chat message.
     */
    private final ChatMessage chatMessage;

    /**
     * A list of content used to augment the original chat message.
     */
    private final List<Content> contents;

    @Builder
    public AugmentationResult(ChatMessage chatMessage, List<Content> contents) {
        this.chatMessage = ensureNotNull(chatMessage, "chatMessage");
        this.contents = copyIfNotNull(contents);
    }

    public ChatMessage chatMessage() {
        return chatMessage;
    }

    public List<Content> contents() {
        return contents;
    }
}
