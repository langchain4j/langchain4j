package dev.langchain4j.store.embedding.pgvector;

/**
 * MetadataHandlerFactory class
 * Use the {@link MetadataStorageConfig#storageMode()} to switch between different Handler implementation
 */
class MetadataHandlerFactory {
    /**
     * Default Constructor
     */
    public MetadataHandlerFactory() {}
    /**
     * Retrieve the handler associated to the config
     * @param config MetadataConfig config
     * @return MetadataHandler
     */
    static MetadataHandler get(MetadataStorageConfig config) {
        switch(config.storageMode()) {
            case COMBINED_JSON:
                return new JSONMetadataHandler(config);
            case COMBINED_JSONB:
                return new JSONBMetadataHandler(config);
            case COLUMN_PER_KEY:
                return new ColumnsMetadataHandler(config);
            default:
                throw new RuntimeException(String.format("Type %s not handled.", config.storageMode()));
        }
    }
}
