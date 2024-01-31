package dev.langchain4j.rag.query;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.RetrievalAugmentor;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents metadata that may be useful or necessary for retrieval or augmentation purposes.
 */
public class Metadata {

    private final UserMessage userMessage;
    private final Object chatMemoryId;
    private final List<ChatMessage> chatMemory;

    public Metadata(UserMessage userMessage, Object chatMemoryId, List<ChatMessage> chatMemory) {
        this.userMessage = ensureNotNull(userMessage, "userMessage");
        this.chatMemoryId = chatMemoryId;
        this.chatMemory = copyIfNotNull(chatMemory);
    }

    /**
     * @return an original {@link UserMessage} passed to the {@link RetrievalAugmentor#augment(UserMessage, Metadata)}.
     */
    public UserMessage userMessage() {
        return userMessage;
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
        return Objects.equals(this.userMessage, that.userMessage)
                && Objects.equals(this.chatMemoryId, that.chatMemoryId)
                && Objects.equals(this.chatMemory, that.chatMemory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userMessage, chatMemoryId, chatMemory);
    }

    @Override
    public String toString() {
        return "Metadata {" +
                " userMessage = " + userMessage +
                ", chatMemoryId = " + chatMemoryId +
                ", chatMemory = " + chatMemory +
                " }";
    }

    public static Metadata from(UserMessage userMessage, Object chatMemoryId, List<ChatMessage> chatMemory) {
        return new Metadata(userMessage, chatMemoryId, chatMemory);
    }
}
