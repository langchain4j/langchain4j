package dev.langchain4j.store.embedding.filter;

/**
 * Parses a filter expression string into a {@link Filter} object.
 * <br>
 * Currently, there is only one implementation: {@code SqlFilterParser}
 * in the {@code langchain4j-embedding-store-filter-parser-sql} module.
 */
public interface FilterParser {

    /**
     * Parses a filter expression string into a {@link Filter} object.
     *
     * @param filter The filter expression as a string.
     * @return A {@link Filter} object.
     */
    Filter parse(String filter);
}
