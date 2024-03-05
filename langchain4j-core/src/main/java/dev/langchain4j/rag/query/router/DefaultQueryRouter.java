package dev.langchain4j.rag.query.router;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.Collection;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableCollection;

/**
 * Default implementation of {@link QueryRouter} intended to be suitable for the majority of use cases.
 * <br>
 * <br>
 * It's important to note that while efforts will be made to avoid breaking changes,
 * the default behavior of this class may be updated in the future if it's found
 * that the current behavior does not adequately serve the majority of use cases.
 * Such changes would be made to benefit both current and future users.
 * <br>
 * <br>
 * This implementation always routes all {@link Query}s
 * to one or multiple {@link ContentRetriever}s provided in the constructor.
 *
 * @see LanguageModelQueryRouter
 */
public class DefaultQueryRouter implements QueryRouter {

    private final Collection<ContentRetriever> contentRetrievers;

    public DefaultQueryRouter(ContentRetriever... contentRetrievers) {
        this(asList(contentRetrievers));
    }

    public DefaultQueryRouter(Collection<ContentRetriever> contentRetrievers) {
        this.contentRetrievers = unmodifiableCollection(ensureNotEmpty(contentRetrievers, "contentRetrievers"));
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        return contentRetrievers;
    }
}
