package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.ChatLanguageModel;

/**
 * A {@link ChatLanguageModel} listener that listens for requests, responses and errors.
 */
@Experimental
public interface ChatModelListener {

    /**
     * This method is called before the request is sent to the model.
     *
     * @param requestContext The request context. It contains the {@link ChatModelRequest} and attributes.
     *                       The attributes can be used to pass data between methods of this listener
     *                       or between multiple listeners.
     */
    @Experimental
    default void onRequest(ChatModelRequestContext requestContext) {

    }

    /**
     * This method is called after the response is received from the model.
     *
     * @param responseContext The response context.
     *                        It contains {@link ChatModelResponse}, corresponding {@link ChatModelRequest} and attributes.
     *                        The attributes can be used to pass data between methods of this listener
     *                        or between multiple listeners.
     */
    @Experimental
    default void onResponse(ChatModelResponseContext responseContext) {

    }

    /**
     * This method is called when an error occurs during interaction with the model.
     *
     * @param errorContext The error context.
     *                     It contains the error, corresponding {@link ChatModelRequest},
     *                     partial {@link ChatModelResponse} (if available) and attributes.
     *                     The attributes can be used to pass data between methods of this listener
     *                     or between multiple listeners.
     */
    @Experimental
    default void onError(ChatModelErrorContext errorContext) {

    }
}
