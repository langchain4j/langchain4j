package dev.langchain4j.rag.content.retriever.listener;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The content retriever response context.
 * It contains retrieved {@link Content}s, corresponding {@link Query}, {@link ContentRetriever} and attributes.
 * The attributes can be used to pass data between methods of a {@link ContentRetrieverListener}
 * or between multiple {@link ContentRetrieverListener}s.
 *
 * @since 1.11.0
 */
@Experimental
public class ContentRetrieverResponseContext {

    private final List<Content> contents;
    private final Query query;
    private final ContentRetriever contentRetriever;
    private final Map<Object, Object> attributes;
    private final UUID invocationId;

    public ContentRetrieverResponseContext(Builder builder) {
        this.contents = copy(ensureNotNull(builder.contents, "contents"));
        this.query = ensureNotNull(builder.query, "query");
        this.contentRetriever = ensureNotNull(builder.contentRetriever, "contentRetriever");
        this.attributes = ensureNotNull(builder.attributes, "attributes");
        this.invocationId = builder.invocationId;
    }

    /**
     * @return the id of the AI Service invocation in which this call is made (the calls made by its tools, guardrails,
     * retrieval, moderation, chat memory, ...), otherwise {@code null}, for example when it is called outside of an AI
     * Service.
     * It is the {@link InvocationContext#invocationId()} of the AI Service invocation, not an id of this call.
     * @since 1.24.0
     */
    @Experimental
    public UUID invocationId() {
        return invocationId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ContentRetrieverResponseContext}.
     *
     * @since 1.11.0
     */
    @Experimental
    public static class Builder {

        private List<Content> contents;
        private Query query;
        private ContentRetriever contentRetriever;
        private Map<Object, Object> attributes;
        private UUID invocationId;

        Builder() {}

        public Builder contents(List<Content> contents) {
            this.contents = contents;
            return this;
        }

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

        /**
         * Sets the id of the AI Service invocation in which the call is made, see {@link #invocationId()} of the
         * context. LangChain4j sets it for the listeners; set it yourself only when you create the context yourself.
         *
         * @since 1.24.0
         */
        @Experimental
        public Builder invocationId(UUID invocationId) {
            this.invocationId = invocationId;
            return this;
        }

        public ContentRetrieverResponseContext build() {
            return new ContentRetrieverResponseContext(this);
        }
    }

    public List<Content> contents() {
        return contents;
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
