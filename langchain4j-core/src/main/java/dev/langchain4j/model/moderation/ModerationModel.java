package dev.langchain4j.model.moderation;

import static dev.langchain4j.model.ModelProvider.OTHER;
import static dev.langchain4j.model.moderation.ModerationModelListenerUtils.onError;
import static dev.langchain4j.model.moderation.ModerationModelListenerUtils.onRequest;
import static dev.langchain4j.model.moderation.ModerationModelListenerUtils.onResponse;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a model that can moderate text.
 */
public interface ModerationModel {

    /**
     * This is the main API to interact with the moderation model.
     *
     * @param moderationRequest a {@link ModerationRequest}, containing all the inputs to the moderation model
     * @return a {@link ModerationResponse}, containing all the outputs from the moderation model
     */
    default ModerationResponse moderate(ModerationRequest moderationRequest) {
        List<ModerationModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        onRequest(moderationRequest, provider(), modelName(), attributes, listeners);
        try {
            ModerationResponse moderationResponse = doModerate(moderationRequest);
            onResponse(moderationResponse, moderationRequest, provider(), modelName(), attributes, listeners);
            return moderationResponse;
        } catch (Exception error) {
            onError(error, moderationRequest, provider(), modelName(), attributes, listeners);
            throw error;
        }
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
        return Response.from(response.moderation(), null, null, response.metadata());
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
        return Response.from(response.moderation(), null, null, response.metadata());
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

    /**
     * Converts a ModerationRequest into a list of text inputs.
     * This is a helper method for implementations.
     *
     * @param moderationRequest the moderation request
     * @return a list of text inputs extracted from the request
     */
    static List<String> toInputs(ModerationRequest moderationRequest) {
        List<String> inputs = new ArrayList<>();
        if (moderationRequest.hasText()) {
            inputs.add(moderationRequest.text());
        }
        if (moderationRequest.hasMessages()) {
            moderationRequest.messages().stream().map(ModerationModel::toText).forEach(inputs::add);
        }
        return inputs;
    }

    /**
     * Converts a ChatMessage to its text representation.
     * This is a helper method for implementations.
     *
     * @param chatMessage the chat message
     * @return the text content of the message
     * @throws IllegalArgumentException if the message type is unsupported
     */
    static String toText(ChatMessage chatMessage) {
        if (chatMessage instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        } else if (chatMessage instanceof UserMessage userMessage) {
            return userMessage.singleText();
        } else if (chatMessage instanceof AiMessage aiMessage) {
            return aiMessage.text();
        } else if (chatMessage instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return toolExecutionResultMessage.text();
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + chatMessage.type());
        }
    }

    /**
     * Returns the list of listeners for this moderation model.
     *
     * @return the list of listeners, or an empty list if none are registered.
     */
    default List<ModerationModelListener> listeners() {
        return List.of();
    }

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
        return "unknown";
    }
}
