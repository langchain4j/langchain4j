package dev.langchain4j.store.embedding.oceanbase;

/**
 * Interface for mapping metadata keys to SQL expressions.
 * This allows for different mapping strategies without exposing internal implementations.
 */
public interface MetadataKeyMapper {
    
    /**
     * Maps a metadata key to a SQL expression that can be used in queries.
     *
     * @param key The metadata key to map
     * @return A SQL expression that references the given metadata key
     */
    String mapKey(String key);
}
