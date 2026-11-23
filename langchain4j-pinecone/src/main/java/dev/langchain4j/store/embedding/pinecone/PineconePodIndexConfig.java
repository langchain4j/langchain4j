package dev.langchain4j.store.embedding.pinecone;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import io.pinecone.clients.Pinecone;

public class PineconePodIndexConfig implements PineconeIndexConfig {

    private final Integer dimension;
    private final String environment;
    private final String podType;

    PineconePodIndexConfig(Integer dimension, String environment, String podType) {
        environment = ensureNotNull(environment, "environment");
        podType = ensureNotNull(podType, "podType");
        ensureNotNull(dimension, "dimension");

        this.dimension = dimension;
        this.environment = environment;
        this.podType = podType;
    }

    @Override
    public void createIndex(Pinecone pinecone, String index) {
        ensureNotNull(index, "index");
        ensureNotNull(pinecone, "pinecone");
        pinecone.createPodsIndex(index, dimension, environment, podType, "cosine");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer dimension;
        private String environment;
        private String podType;

        /**
         * Sets the vector dimension of the index (required).
         *
         * @param dimension the number of dimensions for stored vectors
         * @return {@code this}
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * Sets the Pinecone environment for the pod-based index (required).
         * Example: {@code "us-east1-gcp"}.
         *
         * @param environment the Pinecone environment
         * @return {@code this}
         */
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Sets the pod type for the index (required).
         * Example: {@code "p1.x1"}.
         *
         * @param podType the pod type
         * @return {@code this}
         */
        public Builder podType(String podType) {
            this.podType = podType;
            return this;
        }

        /**
         * Builds the {@link PineconePodIndexConfig}.
         *
         * @return the configured {@link PineconePodIndexConfig}
         */
        public PineconePodIndexConfig build() {
            return new PineconePodIndexConfig(dimension, environment, podType);
        }
    }
}
