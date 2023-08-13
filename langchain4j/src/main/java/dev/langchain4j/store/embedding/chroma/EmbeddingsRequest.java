package dev.langchain4j.store.embedding.chroma;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class EmbeddingsRequest {

    private final List<float[]> embeddings;
    private final List<Map<String, String>> metadatas;
    private final List<String> documents;
    private final List<String> ids;
    private final boolean incrementIndex;

    public EmbeddingsRequest(Builder builder) {
        this.embeddings = builder.embeddings;
        this.metadatas = builder.chromaMetadata;
        this.documents = builder.documents;
        this.ids = builder.ids;
        this.incrementIndex = builder.incrementIndex;
    }

    public List<float[]> getEmbeddings() {
        return embeddings;
    }

    public List<Map<String, String>> getMetadatas() {
        return metadatas;
    }

    public List<String> getDocuments() {
        return documents;
    }

    public List<String> getIds() {
        return ids;
    }

    public boolean isIncrementIndex() {
        return incrementIndex;
    }

    @Override
    public String toString() {
        return "EmbeddingsRequest{" +
                "embeddings=" + embeddings +
                ", metadatas=" + metadatas +
                ", documents=" + documents +
                ", ids=" + ids +
                ", incrementIndex=" + incrementIndex +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<float[]> embeddings = new ArrayList<>();
        private List<Map<String, String>> chromaMetadata = new ArrayList<>();
        private List<String> documents = new ArrayList<>();
        private List<String> ids = new ArrayList<>();
        private boolean incrementIndex;

        public Builder embeddings(List<float[]> embeddings) {
            if (embeddings != null) {
                this.embeddings = embeddings;
            }
            return this;
        }

        public Builder metadatas(List<Map<String, String>> chromaMetadata) {
            if (chromaMetadata != null) {
                this.chromaMetadata = chromaMetadata;
            }
            return this;
        }

        public Builder documents(List<String> documents) {
            if (documents != null) {
                this.documents = documents;
            }
            return this;
        }

        public Builder ids(List<String> ids) {
            if (ids != null) {
                this.ids = ids;
            }
            return this;
        }

        public Builder incrementIndex(boolean incrementIndex) {
            this.incrementIndex = incrementIndex;
            return this;
        }

        EmbeddingsRequest build() {
            return new EmbeddingsRequest(this);
        }
    }

}
