package dev.langchain4j.model.moderation.listener;

import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;

/**
 * A {@link ModerationModel} listener that listens for requests, responses and errors.
 */
public interface ModerationModelListener {

    /**
     * This method is called before the request is sent to the moderation model.
     *
     * @param requestContext The request context. It contains the {@link ModerationRequest} and attributes.
     *                       The attributes can be used to pass data between methods of this listener
     *                       or between multiple listeners.
     */
    default void onRequest(ModerationModelRequestContext requestContext) {}

    /**
     * This method is called after the response is received from the model.
     *
     * @param responseContext The response context.
     *                        It contains {@link ModerationResponse}, corresponding {@link ModerationRequest} and attributes.
     *                        The attributes can be used to pass data between methods of this listener
     *                        or between multiple listeners.
     */
    default void onResponse(ModerationModelResponseContext responseContext) {}

    /**
     * This method is called when an error occurs during interaction with the model.
     *
     * @param errorContext The error context.
     *                     It contains the error, corresponding {@link ModerationRequest} and attributes.
     *                     The attributes can be used to pass data between methods of this listener
     *                     or between multiple listeners.
     */
    default void onError(ModerationModelErrorContext errorContext) {}
}
