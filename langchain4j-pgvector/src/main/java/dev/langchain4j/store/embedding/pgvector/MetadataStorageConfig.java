package dev.langchain4j.store.embedding.pgvector;

import java.util.List;

/**
 * Metadata configuration.
 */
public interface MetadataStorageConfig {
    /**
     * Metadata storage mode
     * <ul>
     * <li>COMBINED_JSON: For dynamic metadata, when you don't know the list of metadata that will be used.
     * <li>COMBINED_JSONB: Same as JSON, but stored in a binary way. Optimized for query on large dataset.
     * <li>COLUMN_PER_KEY: for static metadata, when you know in advance the list of metadata
     * </ul>
     * @return Metadata storage mode
     */
    MetadataStorageMode storageMode();
    /**
     * SQL definition of metadata field(s) list.
     * Example:
     * <ul>
     * <li>COMBINED_JSON: <code>Collections.singletonList("metadata JSON NULL")</code>
     * <li>COMBINED_JSONB: <code>Collections.singletonList("metadata JSONB NULL")</code>
     * <li>COLUMN_PER_KEY: <code>Arrays.asList("condominium_id uuid null", "user uuid null")</code>
     * </ul>
     * @return list of column definitions
     */
    List<String> columnDefinitions();
    /**
     * Metadata Indexes, list of fields to use as index.
     * Example:
     * <ul>
     * <li>COMBINED_JSON: <code>Collections.singletonList("metadata")</code> or
     * <code>Arrays.asList("(metadata-&gt;'key')", "(metadata-&gt;'name')", "(metadata-&gt;'age')")</code>
     * <li>COMBINED_JSONB: <code>Collections.singletonList("metadata")</code> or
     * <code>Arrays.asList("(metadata-&gt;'key')", "(metadata-&gt;'name')", "(metadata-&gt;'age')")</code>
     * <li>COLUMN_PER_KEY: <code>Arrays.asList("key", "name", "age")</code>
     * </ul>
     * @return Metadata Indexes list
     */
    List<String> indexes();
    /**
     * Index Type:
     * <ul>
     * <li>BTREE (default)
     * <li>GIN
     * <li>... postgres indexes
     * </ul>
     * @return Index Type
     */
    String indexType();
}
