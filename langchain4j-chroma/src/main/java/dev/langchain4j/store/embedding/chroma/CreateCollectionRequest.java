package dev.langchain4j.store.embedding.chroma;

import java.util.HashMap;
import java.util.Map;

class CreateCollectionRequest {

    private final String name;
    private final Map<String, Object> metadata;

    /**
     * Currently, cosine distance is always used as the distance method for chroma implementation
     */
    CreateCollectionRequest(String name) {
        this.name = name;
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("hnsw:space", "cosine");
        this.metadata = metadata;
    }
}
