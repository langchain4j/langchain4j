package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.Internal;
import java.util.Map;

@Internal
class Collection {

    private static final String DEFAULT_DISTANCE_FUNCTION = "l2";
    private static final String SPACE_KEY = "space";
    private static final String HNSW_SPACE_METADATA_KEY = "hnsw:space";
    private static final String HNSW_KEY = "hnsw";
    private static final String LEGACY_HNSW_KEY = "hnsw_configuration";
    private static final String SPANN_KEY = "spann";

    private String id;
    private String name;
    private Map<String, Object> metadata;
    private Map<String, Object> configurationJson;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Map<String, Object> getConfigurationJson() {
        return configurationJson;
    }

    String distanceFunction() {
        // "metadata" is checked first on purpose: Chroma 0.5.x reports the default "l2" in "configuration_json"
        // even for a collection that was created with a different metric through "metadata"
        String space = metadataSpace();
        if (space != null) {
            return space;
        }

        space = configuredSpace();
        return space != null ? space : DEFAULT_DISTANCE_FUNCTION;
    }

    private String metadataSpace() {
        if (metadata == null) {
            return null;
        }
        Object space = metadata.get(HNSW_SPACE_METADATA_KEY);
        return space == null ? null : space.toString();
    }

    private String configuredSpace() {
        if (configurationJson == null) {
            return null;
        }

        String space = spaceFrom(configurationJson.get(HNSW_KEY));
        if (space != null) {
            return space;
        }

        space = spaceFrom(configurationJson.get(LEGACY_HNSW_KEY));
        if (space != null) {
            return space;
        }

        return spaceFrom(configurationJson.get(SPANN_KEY));
    }

    private static String spaceFrom(Object configuration) {
        if (!(configuration instanceof Map<?, ?> configurationMap)) {
            return null;
        }
        Object space = configurationMap.get(SPACE_KEY);
        return space == null ? null : space.toString();
    }
}
