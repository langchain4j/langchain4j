package dev.langchain4j.model.chat.observability;

import dev.langchain4j.Experimental;

/**
 * TODO
 */
// TODO name
// TODO package
@Experimental
public interface ChatLanguageModelListener {


    /**
     * TODO
     *
     * @param id
     * @param request
     */
    // TODO names
    @Experimental
    default void onRequest(String id, ChatLanguageModelRequest request) {

    }

    /**
     * TODO
     *
     * @param id
     * @param response
     */
    // TODO names
    // TODO accept Response<AiMessage> ?
    @Experimental
    default void onResponse(String id, ChatLanguageModelResponse response) {

    }

    // TODO onError?
}
