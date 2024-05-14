package dev.langchain4j.model.listener;

import dev.langchain4j.Experimental;

/**
 * TODO
 */
@Experimental
public interface ModelListener<Request, Response> {

    /**
     * TODO
     *
     * @param request
     */
    @Experimental
    default void onRequest(Request request) {

    }

    /**
     * TODO
     *
     * @param request
     * @param response
     */
    @Experimental
    default void onResponse(Request request, Response response) {

    }

    /**
     * TODO
     * TODO can be error and sometimes response (streaming?)
     *
     * @param request
     * @param response TODO what was received BEFORE the error occurred (if any)
     * @param error
     */
    @Experimental
    default void onError(Request request, Response response, Throwable error) {

    }
}
