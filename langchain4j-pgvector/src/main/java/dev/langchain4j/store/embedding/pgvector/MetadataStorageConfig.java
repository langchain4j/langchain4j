package dev.langchain4j.store.embedding.pgvector;

import java.util.List;
import java.util.Optional;

/**
 * Metadata configuration.
 */
public interface MetadataStorageConfig {
    /**
     * Metadata storage mode
     * <ul>
     * <li>COLUMN_PER_KEY: for static metadata, when you know in advance the list of metadata
     * <li>COMBINED_JSON: For dynamic metadata, when you don't know the list of metadata that will be used.
     * <li>COMBINED_JSONB: Same as JSON, but stored in a binary way. Optimized for query on large dataset.
     * </ul>
     * <p>
     * Default value: COMBINED_JSON
     * @return Metadata storage mode
     */
    MetadataStorageMode storageMode();
    /**
     * SQL definition of metadata field(s).
     * By default, "metadata JSON NULL" configured for JSON metadata type
     * Ex: condominium_id uuid null, user uuid null
     * @return Metadata Definition
     */
    List<String> definition();
    /**
     * Metadata Indexes, list of fields to use as index
     * example:
     * <ul>
     * <li>JSON: metadata or (metadata-&gt;'key'), (metadata-&gt;'name'), (metadata-&gt;'age')
     * <li>JSONB: (metadata_b-&gt;'key'), (metadata_b-&gt;'name'), (metadata_b-&gt;'age')
     * <li>COLUMNS: key, name, age
     * </ul>
     * @return Metadata Indexes
     */
    Optional<List<String>> indexes();
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
