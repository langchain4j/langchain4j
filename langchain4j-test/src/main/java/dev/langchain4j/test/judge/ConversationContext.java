package dev.langchain4j.test.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

/**
 * A {@link JudgeContext} with the conversation that the judged text answers, for example the messages of the last
 * request an AI Service sent to the chat model: its system message, the messages from its chat memory, the user
 * message and the tool calls and results. It lets the judge check criteria that refer to the conversation, such as
 * "uses only facts from the tool results".
 */
@Experimental
public final class ConversationContext implements JudgeContext {

    private final List<ChatMessage> messages;

    private ConversationContext(List<? extends ChatMessage> messages) {
        ensureNotEmpty(messages, "conversation");
        messages.forEach(message -> ensureNotNull(message, "message"));
        this.messages = List.copyOf(messages);
    }

    /**
     * Creates a context with the given conversation.
     *
     * @throws IllegalArgumentException if the conversation is empty or contains {@code null}
     */
    public static ConversationContext from(List<? extends ChatMessage> conversation) {
        return new ConversationContext(conversation);
    }

    /**
     * The messages of the conversation, in order. Never empty, immutable.
     */
    public List<ChatMessage> messages() {
        return messages;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConversationContext that && messages.equals(that.messages);
    }

    @Override
    public int hashCode() {
        return messages.hashCode();
    }

    @Override
    public String toString() {
        return "ConversationContext{messages=" + messages + '}';
    }
}
