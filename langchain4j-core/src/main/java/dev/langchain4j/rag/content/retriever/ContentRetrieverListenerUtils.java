package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverErrorContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverRequestContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverResponseContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
class ContentRetrieverListenerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ContentRetrieverListenerUtils.class);

    private ContentRetrieverListenerUtils() {}

    static void onRequest(ContentRetrieverRequestContext requestContext, List<ContentRetrieverListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the content retriever listener. "
                                + "This exception has been ignored.",
                        e);
            }
        });
    }

    static void onResponse(ContentRetrieverResponseContext responseContext, List<ContentRetrieverListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        listeners.forEach(listener -> {
            try {
                listener.onResponse(responseContext);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the content retriever listener. "
                                + "This exception has been ignored.",
                        e);
            }
        });
    }

    static void onError(ContentRetrieverErrorContext errorContext, List<ContentRetrieverListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        listeners.forEach(listener -> {
            try {
                listener.onError(errorContext);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the content retriever listener. "
                                + "This exception has been ignored.",
                        e);
            }
        });
    }
}
