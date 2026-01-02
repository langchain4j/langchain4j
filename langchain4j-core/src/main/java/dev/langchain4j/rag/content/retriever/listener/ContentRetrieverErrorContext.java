package dev.langchain4j.rag.content.retriever.listener;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The content retriever error context.
 * It contains the error, corresponding {@link Query}, {@link ContentRetriever} and attributes.
 * The attributes can be used to pass data between methods of a {@link ContentRetrieverListener}
 * or between multiple {@link ContentRetrieverListener}s.
 */
public class ContentRetrieverErrorContext {

    private final Throwable error;
    private final Query query;
    private final ContentRetriever contentRetriever;
    private final Map<Object, Object> attributes;

    public ContentRetrieverErrorContext(
            Throwable error, Query query, ContentRetriever contentRetriever, Map<Object, Object> attributes) {
        this.error = ensureNotNull(error, "error");
        this.query = ensureNotNull(query, "query");
        this.contentRetriever = ensureNotNull(contentRetriever, "contentRetriever");
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @return The error that occurred.
     */
    public Throwable error() {
        return error;
    }

    public Query query() {
        return query;
    }

    public ContentRetriever contentRetriever() {
        return contentRetriever;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of a {@link ContentRetrieverListener}
     * or between multiple {@link ContentRetrieverListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }
}


