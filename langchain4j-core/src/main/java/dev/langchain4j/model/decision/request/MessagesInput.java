package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A {@link DecisionInput} that is a conversation: messages in the order they were exchanged, for example a chat
 * between a user and an AI assistant, including the tools the assistant called
 * ({@link dev.langchain4j.data.message.AiMessage#toolExecutionRequests()}) and their results
 * ({@link dev.langchain4j.data.message.ToolExecutionResultMessage}). Which kinds of messages and contents are
 * supported depends on the decision model.
 *
 * @since 1.23.0
 */
@Experimental
public final class MessagesInput implements DecisionInput {

    private final List<ChatMessage> messages;

    private MessagesInput(List<? extends ChatMessage> messages) {
        List<ChatMessage> copy = new ArrayList<>(ensureNotEmpty(messages, "messages"));
        if (copy.contains(null)) {
            throw new IllegalArgumentException("messages cannot contain null");
        }
        this.messages = Collections.unmodifiableList(copy);
    }

    /**
     * Creates the input from a copy of the given messages.
     *
     * @throws IllegalArgumentException if {@code messages} is {@code null} or empty, or contains {@code null}
     */
    public static MessagesInput from(List<? extends ChatMessage> messages) {
        return new MessagesInput(messages);
    }

    /**
     * The messages, in order. The list is unmodifiable.
     */
    public List<ChatMessage> messages() {
        return messages;
    }

    @Override
    public DecisionInputType type() {
        return DecisionInputType.MESSAGES;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MessagesInput that && messages.equals(that.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages);
    }

    @Override
    public String toString() {
        return "MessagesInput{types=" + messages.stream().map(ChatMessage::type).toList() + '}';
    }
}
