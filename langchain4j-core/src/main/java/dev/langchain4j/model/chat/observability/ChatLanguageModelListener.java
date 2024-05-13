package dev.langchain4j.model.chat.observability;

import dev.langchain4j.Experimental;

/**
 * TODO
 */
// TODO name: ChatLanguageModelObserver?
// TODO package
@Experimental
public interface ChatLanguageModelListener {


    /**
     * TODO
     *
     * @param request
     */
    // TODO names
    @Experimental
    default void onRequest(ChatLanguageModelRequest request) {

    }

    /**
     * TODO
     *
     * @param request
     * @param response
     */
    // TODO names
    @Experimental
    default void onResponse(ChatLanguageModelRequest request, ChatLanguageModelResponse response) {

    }

    /**
     * TODO
     *
     * @param request
     * @param error
     */
    // TODO names
    @Experimental
    default void onError(ChatLanguageModelRequest request, Throwable error) {

    }
}
