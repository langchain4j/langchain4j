package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.rag.query.Query;

import java.util.Collection;

import static java.util.Collections.singletonList;

/**
 * Default implementation of {@link QueryTransformer} intended to be suitable for the majority of use cases.
 * <br>
 * <br>
 * It's important to note that while efforts will be made to avoid breaking changes,
 * the default behavior of this class may be updated in the future if it's found
 * that the current behavior does not adequately serve the majority of use cases.
 * Such changes would be made to benefit both current and future users.
 * <br>
 * <br>
 * This implementation simply returns the provided {@link Query} without any transformation.
 *
 * @see CompressingQueryTransformer
 * @see ExpandingQueryTransformer
 */
public class DefaultQueryTransformer implements QueryTransformer {

    @Override
    public Collection<Query> transform(Query query) {
        return singletonList(query);
    }
}
