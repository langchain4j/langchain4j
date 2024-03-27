package dev.langchain4j.store.embedding.pgvector;

public class MetadataHandlerFactory {
    public static MetadataHandler get(MetadataConfig config) {
        if (config.type().equals("JSON")) {
            return new JSONMetadataHandler(config);
        } else if (config.type().equals("JSONB")) {
            return new JSONBMetadataHandler(config);
        } else if (config.type().equals("COLUMNS")) {
            return new ColumnsMetadataHandler(config);
        } else {
            throw new RuntimeException(String.format("Type %s not handled.", config.type()));
        }
    }
}
