package dev.langchain4j.model.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * A generic model listener.
 * It can listen for requests to and responses from various model types,
 * such as {@link ChatLanguageModel}, {@link StreamingChatLanguageModel}, {@link EmbeddingModel}, etc.
 */
@Experimental
public interface ModelListener<Request, RequestResult extends ModelListener.OnRequestResult<Request>, Response> {

    /**
     * This method is called before the request is sent to the model.
     *
     * @param request The request to the model.
     */
    @Experimental
    default RequestResult onRequest(Request request) {
        return null;
    }

    /**
     * This method is called after the response is received from the model.
     *
     * @param response The response from the model.
     * @param request  The request this response corresponds to.
     */
    @Experimental
    default void onResponse(Response response, RequestResult request) {

    }

    /**
     * This method is called when an error occurs.
     * <br>
     * When streaming (e.g., using {@link StreamingChatLanguageModel}),
     * the {@code response} might contain a partial response that was received before the error occurred.
     *
     * @param error    The error that occurred.
     * @param response The partial response, if available.
     * @param request  The request this error corresponds to.
     */
    @Experimental
    default void onError(Throwable error, Response response, RequestResult request) {

    }

    /**
     * This name is horrible, and should be replaced by something better
     */
    interface OnRequestResult<Request> {
        Request request();
    }
}
