package dev.langchain4j.model.moderation;

import static dev.langchain4j.model.ModelProvider.OTHER;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;
import java.util.List;

/**
 * Represents a model that can moderate text.
 */
public interface ModerationModel {

    /**
     * Returns the model provider for this moderation model.
     *
     * @return the model provider.
     */
    default ModelProvider provider() {
        return OTHER;
    }

    /**
     * Returns the model name for this moderation model.
     *
     * @return the model name, or {@code null} if not available.
     */
    default String modelName() {
        return null;
    }

    /**
     * This is the main API to interact with the moderation model.
     *
     * @param moderationRequest a {@link ModerationRequest}, containing all the inputs to the moderation model
     * @return a {@link ModerationResponse}, containing all the outputs from the moderation model
     */
    default ModerationResponse moderate(ModerationRequest moderationRequest) {
        return doModerate(moderationRequest);
    }

    /**
     * Performs the actual moderation. This method should be overridden by implementations.
     *
     * @param moderationRequest the moderation request.
     * @return the moderation response.
     */
    default ModerationResponse doModerate(ModerationRequest moderationRequest) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Moderates the given text.
     *
     * @param text the text to moderate.
     * @return the moderation {@code Response}.
     */
    default Response<Moderation> moderate(String text) {
        ModerationRequest request = ModerationRequest.builder().text(text).build();
        ModerationResponse response = moderate(request);
        return Response.from(response.moderation(), response.tokenUsage(), null, response.metadata());
    }

    /**
     * Moderates the given prompt.
     *
     * @param prompt the prompt to moderate.
     * @return the moderation {@code Response}.
     */
    default Response<Moderation> moderate(Prompt prompt) {
        return moderate(prompt.text());
    }

    /**
     * Moderates the given chat message.
     *
     * @param message the chat message to moderate.
     * @return the moderation {@code Response}.
     */
    default Response<Moderation> moderate(ChatMessage message) {
        return moderate(List.of(message));
    }

    /**
     * Moderates the given list of chat messages.
     *
     * @param messages the list of chat messages to moderate.
     * @return the moderation {@code Response}.
     */
    default Response<Moderation> moderate(List<ChatMessage> messages) {
        ModerationRequest request =
                ModerationRequest.builder().messages(messages).build();
        ModerationResponse response = moderate(request);
        return Response.from(response.moderation(), response.tokenUsage(), null, response.metadata());
    }

    /**
     * Moderates the given text segment.
     *
     * @param textSegment the text segment to moderate.
     * @return the moderation {@code Response}.
     */
    default Response<Moderation> moderate(TextSegment textSegment) {
        return moderate(textSegment.text());
    }
}
