package dev.langchain4j.model.moderation;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.OTHER;
import static dev.langchain4j.model.moderation.ModerationModelListenerUtils.onError;
import static dev.langchain4j.model.moderation.ModerationModelListenerUtils.onRequest;
import static dev.langchain4j.model.moderation.ModerationModelListenerUtils.onResponse;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.output.Response;
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
        ModerationRequest finalRequest = moderationRequest.toBuilder()
                .modelName(getOrDefault(moderationRequest.modelName(), modelName()))
                .build();

        ModelProvider modelProvider = provider();
        List<ModerationModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        onRequest(finalRequest, modelProvider, attributes, listeners);
        try {
            ModerationResponse moderationResponse = doModerate(finalRequest);
            onResponse(moderationResponse, finalRequest, modelProvider, attributes, listeners);
            return moderationResponse;
        } catch (Exception error) {
            onError(error, finalRequest, modelProvider, attributes, listeners);
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
        return moderate(List.of(text));
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
     * Moderates the given list of texts.
     *
     * @param texts the list of texts to moderate.
     * @return the moderation {@code Response}.
     */
    default Response<Moderation> moderate(List<String> texts) {
        ModerationRequest request = ModerationRequest.builder().texts(texts).build();
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
     * Moderates the given chat message.
     *
     * @param message the chat message to moderate.
     * @return the moderation {@code Response}.
     * @deprecated since 2.0.0, use {@link #moderate(String)} with {@link ChatMessage#text()} instead.
     */
    @Deprecated(since = "2.0.0")
    default Response<Moderation> moderate(ChatMessage message) {
        return moderate(message.text());
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
     * @return the model name, or {@code "unknown"} if not available.
     */
    default String modelName() {
        return "unknown";
    }
}
