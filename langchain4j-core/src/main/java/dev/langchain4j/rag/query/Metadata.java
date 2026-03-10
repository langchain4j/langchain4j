package dev.langchain4j.rag.query;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.RetrievalAugmentor;
import java.util.List;
import java.util.Objects;

/**
 * Represents metadata that may be useful or necessary for retrieval or augmentation purposes.
 */
public class Metadata {

    private final ChatMessage chatMessage;
    private final SystemMessage systemMessage;
    private final List<ChatMessage> chatMemory;
    private final InvocationContext invocationContext;

    public Metadata(Builder builder) {
        this.chatMessage = ensureNotNull(builder.chatMessage, "chatMessage");
        this.systemMessage = builder.systemMessage;
        this.chatMemory = copy(builder.chatMemory);
        this.invocationContext = ensureNotNull(builder.invocationContext, "invocationContext");
    }

    public Metadata(ChatMessage chatMessage, Object chatMemoryId, List<ChatMessage> chatMemory) {
        this.chatMessage = ensureNotNull(chatMessage, "chatMessage");
        this.systemMessage = null;
        this.chatMemory = copy(chatMemory);
        this.invocationContext =
                InvocationContext.builder().chatMemoryId(chatMemoryId).build();
    }

    public Metadata(
            ChatMessage chatMessage, SystemMessage systemMessage, Object chatMemoryId, List<ChatMessage> chatMemory) {
        this.chatMessage = ensureNotNull(chatMessage, "chatMessage");
        this.systemMessage = ensureNotNull(systemMessage, "systemMessage");
        this.chatMemory = copy(chatMemory);
        this.invocationContext =
                InvocationContext.builder().chatMemoryId(chatMemoryId).build();
    }

    /**
     * @return an original {@link ChatMessage} passed to the {@link RetrievalAugmentor#augment(AugmentationRequest)}.
     */
    public ChatMessage chatMessage() {
        return chatMessage;
    }

    /**
     * @return the effective {@link SystemMessage} from the AI Service invocation, if any
     */
    public SystemMessage systemMessage() {
        return systemMessage;
    }

    /**
     * @return a chat memory ID. Present when {@link ChatMemory} is used. Can be used to distinguish between users.
     * See {@code @dev.langchain4j.service.MemoryId} annotation from a {@code dev.langchain4j:langchain4j} module.
     */
    public Object chatMemoryId() {
        return invocationContext.chatMemoryId();
    }

    /**
     * @return previous messages in the {@link ChatMemory}. Present when {@link ChatMemory} is used.
     * Can be used to get more details about the context (conversation) in which the {@link Query} originated.
     */
    public List<ChatMessage> chatMemory() {
        return chatMemory;
    }

    /**
     * @since 1.6.0
     */
    public InvocationContext invocationContext() {
        return invocationContext;
    }

    /**
     * @since 1.6.0
     */
    public InvocationParameters invocationParameters() {
        return invocationContext.invocationParameters();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Metadata that = (Metadata) o;
        return Objects.equals(this.chatMessage, that.chatMessage)
                && Objects.equals(this.systemMessage, that.systemMessage)
                && Objects.equals(this.chatMemory, that.chatMemory)
                && Objects.equals(this.invocationContext, that.invocationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatMessage, systemMessage, chatMemory, invocationContext);
    }

    @Override
    public String toString() {
        return "Metadata {" + " chatMessage = "
                + chatMessage + ", systemMessage = "
                + systemMessage + ", chatMemory = "
                + chatMemory + ", invocationContext = "
                + invocationContext + " }";
    }

    public static Metadata from(ChatMessage chatMessage, Object chatMemoryId, List<ChatMessage> chatMemory) {
        return new Metadata(chatMessage, chatMemoryId, chatMemory);
    }

    public static Metadata from(
            ChatMessage chatMessage, SystemMessage systemMessage, Object chatMemoryId, List<ChatMessage> chatMemory) {
        return new Metadata(chatMessage, systemMessage, chatMemoryId, chatMemory);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ChatMessage chatMessage;
        private SystemMessage systemMessage;
        private List<ChatMessage> chatMemory;
        private InvocationContext invocationContext;

        public Builder chatMessage(ChatMessage chatMessage) {
            this.chatMessage = chatMessage;
            return this;
        }

        public Builder systemMessage(SystemMessage systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        public Builder chatMemory(List<ChatMessage> chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        public Metadata build() {
            return new Metadata(this);
        }
    }
}
