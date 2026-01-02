package dev.langchain4j.rag.content.retriever.listener;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The content retriever request context.
 * It contains the {@link Query}, {@link ContentRetriever} and attributes.
 * The attributes can be used to pass data between methods of a {@link ContentRetrieverListener}
 * or between multiple {@link ContentRetrieverListener}s.
 */
public class ContentRetrieverRequestContext {

    private final Query query;
    private final ContentRetriever contentRetriever;
    private final Map<Object, Object> attributes;

    public ContentRetrieverRequestContext(Query query, ContentRetriever contentRetriever, Map<Object, Object> attributes) {
        this.query = ensureNotNull(query, "query");
        this.contentRetriever = ensureNotNull(contentRetriever, "contentRetriever");
        this.attributes = ensureNotNull(attributes, "attributes");
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


