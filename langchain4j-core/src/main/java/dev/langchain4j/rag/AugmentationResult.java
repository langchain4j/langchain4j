package dev.langchain4j.rag;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.rag.content.Content;

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

    /**
     * Whether to skip the content injector.
     */
    private final boolean skipInjection;

    public AugmentationResult(ChatMessage chatMessage, List<Content> contents, boolean skipInjection) {
        this.chatMessage = ensureNotNull(chatMessage, "chatMessage");
        this.contents = copyIfNotNull(contents);
        this.skipInjection = skipInjection;
    }

    public static AugmentationResultBuilder builder() {
        return new AugmentationResultBuilder();
    }

    public ChatMessage chatMessage() {
        return chatMessage;
    }

    public List<Content> contents() {
        return contents;
    }

    public boolean isSkipInjection() {
        return skipInjection;
    }

    public static class AugmentationResultBuilder {
        private ChatMessage chatMessage;
        private List<Content> contents;
        private boolean skipInjection;

        AugmentationResultBuilder() {
        }

        public AugmentationResultBuilder chatMessage(ChatMessage chatMessage) {
            this.chatMessage = chatMessage;
            return this;
        }

        public AugmentationResultBuilder contents(List<Content> contents) {
            this.contents = contents;
            return this;
        }

        public AugmentationResultBuilder skipInjection(boolean skipInjection) {
            this.skipInjection = skipInjection;
            return this;
        }

        public AugmentationResult build() {
            return new AugmentationResult(this.chatMessage, this.contents, this.skipInjection);
        }

        public String toString() {
            return "AugmentationResult.AugmentationResultBuilder(chatMessage=" + this.chatMessage + ", contents=" + this.contents + ", skipInjection=" + this.skipInjection + ")";
        }
    }
}
