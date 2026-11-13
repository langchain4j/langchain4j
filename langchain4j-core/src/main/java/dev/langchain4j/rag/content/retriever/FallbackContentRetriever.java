package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.function.Predicate;

/**
 * A {@link ContentRetriever} that delegates to a fallback retriever when the primary retrieval does not produce a
 * usable result.
 *
 * <p>By default, fallback is triggered when the primary retriever returns {@code null}, returns an empty list, or
 * throws a {@link RuntimeException}. A custom predicate can additionally describe domain-specific "empty" results,
 * for example a structured database response that contains a header but no rows.
 */
public class FallbackContentRetriever implements ContentRetriever {

    private final ContentRetriever primaryRetriever;
    private final ContentRetriever fallbackRetriever;
    private final Predicate<List<Content>> fallbackCondition;
    private final boolean fallbackOnException;

    public FallbackContentRetriever(ContentRetriever primaryRetriever, ContentRetriever fallbackRetriever) {
        this(primaryRetriever, fallbackRetriever, results -> results == null || results.isEmpty(), true);
    }

    private FallbackContentRetriever(
            ContentRetriever primaryRetriever,
            ContentRetriever fallbackRetriever,
            Predicate<List<Content>> fallbackCondition,
            boolean fallbackOnException) {
        this.primaryRetriever = ensureNotNull(primaryRetriever, "primaryRetriever");
        this.fallbackRetriever = ensureNotNull(fallbackRetriever, "fallbackRetriever");
        this.fallbackCondition = ensureNotNull(fallbackCondition, "fallbackCondition");
        this.fallbackOnException = fallbackOnException;
    }

    @Override
    public List<Content> retrieve(Query query) {
        List<Content> contents;
        try {
            contents = primaryRetriever.retrieve(query);
        } catch (RuntimeException e) {
            if (!fallbackOnException) {
                throw e;
            }
            return retrieveFromFallback(query, e);
        }

        if (fallbackCondition.test(contents)) {
            return fallbackRetriever.retrieve(query);
        }
        return contents;
    }

    private List<Content> retrieveFromFallback(Query query, RuntimeException primaryException) {
        try {
            return fallbackRetriever.retrieve(query);
        } catch (RuntimeException fallbackException) {
            fallbackException.addSuppressed(primaryException);
            throw fallbackException;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ContentRetriever primaryRetriever;
        private ContentRetriever fallbackRetriever;
        private Predicate<List<Content>> fallbackCondition = results -> results == null || results.isEmpty();
        private boolean fallbackOnException = true;

        public Builder primaryRetriever(ContentRetriever primaryRetriever) {
            this.primaryRetriever = primaryRetriever;
            return this;
        }

        public Builder fallbackRetriever(ContentRetriever fallbackRetriever) {
            this.fallbackRetriever = fallbackRetriever;
            return this;
        }

        /**
         * Sets the condition that determines whether the primary result should be replaced by the fallback result.
         * The default condition matches {@code null} and empty lists.
         */
        public Builder fallbackCondition(Predicate<List<Content>> fallbackCondition) {
            this.fallbackCondition = fallbackCondition;
            return this;
        }

        /** Sets whether a {@link RuntimeException} from the primary retriever should trigger fallback. */
        public Builder fallbackOnException(boolean fallbackOnException) {
            this.fallbackOnException = fallbackOnException;
            return this;
        }

        public FallbackContentRetriever build() {
            return new FallbackContentRetriever(
                    primaryRetriever, fallbackRetriever, fallbackCondition, fallbackOnException);
        }
    }
}
