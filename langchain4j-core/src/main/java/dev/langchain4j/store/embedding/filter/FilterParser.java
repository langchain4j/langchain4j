package dev.langchain4j.store.embedding.filter;

import dev.langchain4j.Experimental;

/**
 * Parses a filter expression string into a {@link Filter} object.
 * <br>
 * Currently, there is only one implementation: {@code SqlFilterParser}
 * in the {@code langchain4j-embedding-store-filter-parser-sql} module.
 */
@Experimental
public interface FilterParser {

    /**
     * Parses a filter expression string into a {@link Filter} object.
     *
     * @param filter The filter expression as a string.
     * @return A {@link Filter} object.
     */
    Filter parse(String filter);
}
