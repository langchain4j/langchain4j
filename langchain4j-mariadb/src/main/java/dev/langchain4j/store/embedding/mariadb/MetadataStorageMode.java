package dev.langchain4j.store.embedding.mariadb;

/**
 * Metadata storage mode
 * <ul>
 * <li>COLUMN_PER_KEY: for static metadata, when you know in advance the list of metadata
 * <li>COMBINED_JSON: For dynamic metadata, when you don't know the list of metadata that will be used.
 * </ul>
 * <p>
 * Default value: COMBINED_JSON
 */
public enum MetadataStorageMode {
    /**
     * COLUMN_PER_KEY: for static metadata, when you know in advance the list of metadata
     */
    COLUMN_PER_KEY,

    /**
     * COMBINED_JSON: For dynamic metadata, when you don't know the list of metadata that will be used.
     */
    COMBINED_JSON,
}
