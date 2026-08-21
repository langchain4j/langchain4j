package dev.langchain4j.context;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.query.Metadata;

import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a request for context, containing the user's message and invocation metadata.
 * <p>
 * This is the input to {@link ContextProvider#provideContext(ContextRequest)}.
 *
 * @see ContextProvider
 */
@Experimental
public class ContextRequest {

    private final ChatMessage chatMessage;
    private final Metadata metadata;

    public ContextRequest(ChatMessage chatMessage, Metadata metadata) {
        this.chatMessage = ensureNotNull(chatMessage, "chatMessage");
        this.metadata = ensureNotNull(metadata, "metadata");
    }

    /**
     * @return the original user message
     */
    public ChatMessage chatMessage() {
        return chatMessage;
    }

    /**
     * @return the RAG metadata containing chat memory, system message, and invocation context
     */
    public Metadata metadata() {
        return metadata;
    }

    /**
     * @return the invocation parameters, or {@code null} if not available
     */
    public InvocationParameters invocationParameters() {
        return metadata.invocationParameters();
    }

    /**
     * @return the chat memory ID, or {@code null} if not available
     */
    public Object chatMemoryId() {
        return metadata.chatMemoryId();
    }

    /**
     * @return the previous messages in chat memory, or {@code null} if not available
     */
    public List<ChatMessage> chatMemory() {
        return metadata.chatMemory();
    }
}
