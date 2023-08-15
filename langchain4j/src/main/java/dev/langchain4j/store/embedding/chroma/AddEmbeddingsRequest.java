package dev.langchain4j.store.embedding.chroma;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AddEmbeddingsRequest {

    private final List<float[]> embeddings;
    private final List<Map<String, String>> metadatas;
    private final List<String> documents;
    private final List<String> ids;

    public AddEmbeddingsRequest(Builder builder) {
        this.embeddings = builder.embeddings;
        this.metadatas = builder.chromaMetadata;
        this.documents = builder.documents;
        this.ids = builder.ids;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<float[]> embeddings = new ArrayList<>();
        private List<Map<String, String>> chromaMetadata = new ArrayList<>();
        private List<String> documents = new ArrayList<>();
        private List<String> ids = new ArrayList<>();

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

        AddEmbeddingsRequest build() {
            return new AddEmbeddingsRequest(this);
        }
    }

}
