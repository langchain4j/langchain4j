package dev.langchain4j.rag;


import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.query.Metadata;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a request for {@link ChatMessage} augmentation.
 */
public class AugmentationRequest {

    /**
     * The chat message to be augmented.
     * Currently, it is a {@link UserMessage}, but soon it could also be a {@link SystemMessage}.
     */
    private final ChatMessage chatMessage;

    /**
     * Additional metadata related to the augmentation request.
     */
    private final Metadata metadata;

    public AugmentationRequest(ChatMessage chatMessage, Metadata metadata) {
        this.chatMessage = ensureNotNull(chatMessage, "chatMessage");
        this.metadata = ensureNotNull(metadata, "metadata");
    }

    public ChatMessage chatMessage() {
        return chatMessage;
    }

    public Metadata metadata() {
        return metadata;
    }
}
