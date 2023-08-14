package dev.langchain4j.store.embedding.chroma;

import java.util.HashMap;
import java.util.Map;

class CollectionCreationRequest {

    private final String name;
    private final Map<String, String> metadata;

    /**
     * Currently, cosine distance is always used as the distance method for chroma implementation
     */
    CollectionCreationRequest(String name) {
        this.name = name;
        //TODO other distances? Currently, only cosine distance is possible
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("hnsw:space", "cosine");
        this.metadata = metadata;
    }

}
