package dev.langchain4j.store.embedding.pinecone;

import io.pinecone.clients.Pinecone;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class PineconePodIndexConfig implements PineconeIndexConfig {

    private final Integer dimension;
    private final String environment;
    private final String podType;

    PineconePodIndexConfig(Integer dimension,
                           String environment,
                           String podType) {
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

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder podType(String podType) {
            this.podType = podType;
            return this;
        }

        public PineconePodIndexConfig build() {
            return new PineconePodIndexConfig(dimension, environment, podType);
        }
    }
}
