package dev.langchain4j.store.embedding.pinecone;

import io.pinecone.clients.Pinecone;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class PineconePodIndexConfig implements PineconeIndexConfig {

    private final Integer dimension;
    private final String environment;
    private final String podType;
    private final String metric;

    PineconePodIndexConfig(Integer dimension,
                           String environment,
                           String podType,
                           String metric) {
        environment = ensureNotNull(environment, "environment");
        podType = ensureNotNull(podType, "podType");
        ensureNotNull(dimension, "dimension");

        this.dimension = dimension;
        this.environment = environment;
        this.podType = podType;
        this.metric = getOrDefault(metric, "cosine");
    }

    @Override
    public void createIndex(Pinecone pinecone, String index) {
        ensureNotNull(index, "index");
        ensureNotNull(pinecone, "pinecone");
        pinecone.createPodsIndex(index, dimension, environment, podType, metric);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer dimension;
        private String environment;
        private String podType;
        private String metric;

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

        public Builder metric(String metric) {
            this.metric = metric;
            return this;
        }

        public PineconePodIndexConfig build() {
            return new PineconePodIndexConfig(dimension, environment, podType, metric);
        }
    }
}
