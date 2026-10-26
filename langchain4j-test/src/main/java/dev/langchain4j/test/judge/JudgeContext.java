package dev.langchain4j.test.judge;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

/**
 * What the judged text responds to: either the user message ({@link UserMessageContext}) or the whole conversation
 * ({@link ConversationContext}). It lets the judge understand criteria that refer to the question or to the
 * conversation. Only the text is judged, never the context.
 * <pre>{@code
 * JudgeContext question = JudgeContext.from("Where is my booking?");
 * JudgeContext conversation = JudgeContext.from(chatRequest.messages());
 * }</pre>
 * The judges of this package support only these two implementations.
 */
@Experimental
public interface JudgeContext {

    /**
     * Creates a context with the text of the user message that the judged text responds to.
     *
     * @throws IllegalArgumentException if the user message is blank
     */
    static UserMessageContext from(String userMessage) {
        return UserMessageContext.from(userMessage);
    }

    /**
     * Creates a context with the conversation that the judged text answers, for example the messages of the last
     * request an AI Service sent to the chat model.
     *
     * @throws IllegalArgumentException if the conversation is empty or contains {@code null}
     */
    static ConversationContext from(List<? extends ChatMessage> conversation) {
        return ConversationContext.from(conversation);
    }
}
