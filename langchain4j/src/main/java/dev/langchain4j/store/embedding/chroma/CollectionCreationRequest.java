package dev.langchain4j.store.embedding.chroma;

import java.util.HashMap;
import java.util.Map;

class CollectionCreationRequest {

    private final String name;
    private final Map<String, String> metadata;

    /**
     * Currently, cosine distance is always used as the distance method for chroma implementation
     */
    private CollectionCreationRequest(Builder builder) {
        this.name = builder.name;
        //TODO other distances? Currently, only cosine distance is possible
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("hnsw:space", "cosine");
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "CollectionCreationRequest{" +
                "name='" + name + '\'' +
                ", chromaMetadata=" + metadata +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;

        public Builder() {
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public CollectionCreationRequest build() {
            return new CollectionCreationRequest(this);
        }
    }

}
