package dev.langchain4j.store.embedding.mariadb;

import java.util.List;

/**
 * Metadata configuration.
 */
public interface MetadataStorageConfig {
    /**
     * Metadata storage mode
     * <ul>
     * <li>COMBINED_JSON: For dynamic metadata, when you don't know the list of metadata that will be used.
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
     * <li>COLUMN_PER_KEY: <code>Arrays.asList("condominium_id uuid null", "user uuid null")</code>
     * </ul>
     * @return list of column definitions
     */
    List<String> columnDefinitions();

    /**
     * Metadata Indexes, list of fields to use as index.
     * Example:
     * <ul>
     * <li>COLUMN_PER_KEY: <code>Arrays.asList("key", "name", "age")</code>
     * </ul>
     * @return Metadata Indexes list
     */
    List<String> indexes();
}
