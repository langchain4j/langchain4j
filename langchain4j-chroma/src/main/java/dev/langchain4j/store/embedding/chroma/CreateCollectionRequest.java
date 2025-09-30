package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.Internal;
import java.util.HashMap;
import java.util.Map;

@Internal
class CreateCollectionRequest {

    private final String name;
    private final Map<String, Object> metadata;

    /**
     * Currently, cosine distance is always used as the distance method for chroma implementation
     */
    public CreateCollectionRequest(String name) {
        this.name = name;
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("hnsw:space", "cosine");
        this.metadata = metadata;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
