package dev.langchain4j.store.embedding.filter;

/**
 * Parses a string metadata filter expression into a {@link MetadataFilter}.
 * <p>
 * Currently, there is only one implementation: {@code SqlMetadataFilterParser}
 * in the {@code langchain4j-metadata-filter-parser-sql} module.
 */
public interface MetadataFilterParser {

    /**
     * Parses a metadata filter expression string into a {@link MetadataFilter}.
     *
     * @param metadataFilter The metadata filter expression as a string.
     * @return A {@link MetadataFilter}.
     */
    MetadataFilter parse(String metadataFilter);
}
