package dev.langchain4j.store.embedding.chroma;

import java.util.HashMap;
import java.util.Map;

class CollectionCreationRequest {

    private final String name;
    private final Map<String, String> metadata;
    private final boolean get_or_create;

    /**
     * Currently, cosine distance is always used as the distance method for chroma implementation
     */
    private CollectionCreationRequest(Builder builder) {
        this.name = builder.name;
        //TODO other distances? Currently, only cosine distance is possible
        builder.metadata.put("hnsw:space", "cosine");
        this.metadata = builder.metadata;
        this.get_or_create = builder.get_or_create;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public boolean isGet_or_create() {
        return get_or_create;
    }

    @Override
    public String toString() {
        return "CollectionCreationRequest{" +
                "name='" + name + '\'' +
                ", chromaMetadata=" + metadata +
                ", get_or_create=" + get_or_create +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private Map<String, String> metadata = new HashMap<>();
        private boolean get_or_create;

        public Builder() {
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder withGetOrCreate(boolean get_or_create) {
            this.get_or_create = get_or_create;
            return this;
        }

        public CollectionCreationRequest build() {
            return new CollectionCreationRequest(this);
        }
    }

}
