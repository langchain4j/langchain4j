package dev.langchain4j.rag.content.retriever.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.Map;

/**
 * The content retriever request context.
 * It contains the {@link Query}, {@link ContentRetriever} and attributes.
 * The attributes can be used to pass data between methods of a {@link ContentRetrieverListener}
 * or between multiple {@link ContentRetrieverListener}s.
 *
 * @since 1.11.0
 */
@Experimental
public class ContentRetrieverRequestContext {

    private final Query query;
    private final ContentRetriever contentRetriever;
    private final Map<Object, Object> attributes;

    public ContentRetrieverRequestContext(Builder builder) {
        this.query = ensureNotNull(builder.query, "query");
        this.contentRetriever = ensureNotNull(builder.contentRetriever, "contentRetriever");
        this.attributes = ensureNotNull(builder.attributes, "attributes");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ContentRetrieverRequestContext}.
     *
     * @since 1.11.0
     */
    @Experimental
    public static class Builder {

        private Query query;
        private ContentRetriever contentRetriever;
        private Map<Object, Object> attributes;

        Builder() {}

        public Builder query(Query query) {
            this.query = query;
            return this;
        }

        public Builder contentRetriever(ContentRetriever contentRetriever) {
            this.contentRetriever = contentRetriever;
            return this;
        }

        public Builder attributes(Map<Object, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public ContentRetrieverRequestContext build() {
            return new ContentRetrieverRequestContext(this);
        }
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
