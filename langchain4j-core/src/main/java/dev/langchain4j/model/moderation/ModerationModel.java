package dev.langchain4j.model.moderation;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;

import java.util.List;

/**
 * Represents a model that can moderate text.
 */
public interface ModerationModel {

    /**
     * Moderates the given text.
     * @param text the text to moderate.
     * @return the moderation {@code Response}.
     */
    Response<Moderation> moderate(String text);

    /**
     * Moderates the given prompt.
     * @param prompt the prompt to moderate.
     * @return the moderation {@code Response}.
     */
    default Response<Moderation> moderate(Prompt prompt) {
        return moderate(prompt.text());
    }
  
    /**
     * Moderates the given chat message.
     * @param message the chat message to moderate.
     * @return the moderation {@code Response}.
     * 
     * @deprecated Use {@link #moderate(String)} instead.
     * As of 2.0.0, conversion from ChatMessage to text is the caller's responsibility.
     * See https://github.com/langchain4j/langchain4j/issues/4595
     */
    @Deprecated(forRemoval = true)
    default Response<Moderation> moderate(ChatMessage message) {
        return moderate(List.of(message));
    }

    /**
     * Moderates the given list of chat messages.
     * @param messages the list of chat messages to moderate.
     * @return the moderation {@code Response}.
     * 
     * @deprecated Use {@link #moderate(String)} instead.
     * As of 2.0.0, conversion from ChatMessage to text is the caller's responsibility.
     * See https://github.com/langchain4j/langchain4j/issues/4595
     */
    @Deprecated(forRemoval = true)
    Response<Moderation> moderate(List<ChatMessage> messages);

    /**
     * Moderates the given text segment.
     * @param textSegment the text segment to moderate.
     * @return the moderation {@code Response}.
     */
    default Response<Moderation> moderate(TextSegment textSegment) {
        return moderate(textSegment.text());
    }
}
