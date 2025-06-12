package dev.langchain4j.rag.query;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.RetrievalAugmentor;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents metadata that may be useful or necessary for retrieval or augmentation purposes.
 */
public class Metadata {

    private final ChatMessage chatMessage;
    private final Object chatMemoryId;
    private final List<ChatMessage> chatMemory;

    public Metadata(ChatMessage chatMessage, Object chatMemoryId, List<ChatMessage> chatMemory) {
        this.chatMessage = ensureNotNull(chatMessage, "chatMessage");
        this.chatMemoryId = chatMemoryId;
        this.chatMemory = copy(chatMemory);
    }

    /**
     * @return an original {@link ChatMessage} passed to the {@link RetrievalAugmentor#augment(AugmentationRequest)}.
     */
    public ChatMessage chatMessage() {
        return chatMessage;
    }

    /**
     * @return a chat memory ID. Present when {@link ChatMemory} is used. Can be used to distinguish between users.
     * See {@code @dev.langchain4j.service.MemoryId} annotation from a {@code dev.langchain4j:langchain4j} module.
     */
    public Object chatMemoryId() {
        return chatMemoryId;
    }

    /**
     * @return previous messages in the {@link ChatMemory}. Present when {@link ChatMemory} is used.
     * Can be used to get more details about the context (conversation) in which the {@link Query} originated.
     */
    public List<ChatMessage> chatMemory() {
        return chatMemory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Metadata that = (Metadata) o;
        return Objects.equals(this.chatMessage, that.chatMessage)
                && Objects.equals(this.chatMemoryId, that.chatMemoryId)
                && Objects.equals(this.chatMemory, that.chatMemory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatMessage, chatMemoryId, chatMemory);
    }

    @Override
    public String toString() {
        return "Metadata {" +
                " chatMessage = " + chatMessage +
                ", chatMemoryId = " + chatMemoryId +
                ", chatMemory = " + chatMemory +
                " }";
    }

    public static Metadata from(ChatMessage chatMessage, Object chatMemoryId, List<ChatMessage> chatMemory) {
        return new Metadata(chatMessage, chatMemoryId, chatMemory);
    }
}
