package dev.langchain4j.store.embedding.pgvector;

import java.util.List;
import java.util.Optional;

/**
 * Metadata configuration.
 */
public interface MetadataConfig {
    /**
     * Metadata type
     * <ul>
     * <li>COLUMNS: for static metadata, when you know in advance the list of metadata
     * <li>JSON: For dynamic metadata, when you don't know the list of metadata that will be used.
     * <li>JSONB: Same as JSON, but stored in a binary way. Optimized for query on large dataset.
     * </ul>
     * <p>
     * Default value: JSON
     * @return Metadata type
     */
    String type();
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
