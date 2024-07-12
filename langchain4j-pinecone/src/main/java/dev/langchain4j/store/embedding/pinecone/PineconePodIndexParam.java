package dev.langchain4j.store.embedding.pinecone;

import io.pinecone.clients.Pinecone;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class PineconePodIndexParam implements PineconeIndexParam {

    private final String index;
    private final Integer dimension;
    private final String environment;
    private final String podType;

    PineconePodIndexParam(String index,
                          Integer dimension,
                          String environment,
                          String podType) {
        index = ensureNotNull(index, "index");
        environment = ensureNotNull(environment, "environment");
        podType = ensureNotNull(podType, "podType");
        ensureNotNull(dimension, "dimension");

        this.index = index;
        this.dimension = dimension;
        this.environment = environment;
        this.podType = podType;
    }

    @Override
    public void createIndex(Pinecone pinecone) {
        ensureNotNull(pinecone, "pinecone");
        pinecone.createPodsIndex(index, dimension, environment, podType);
    }

    public String getIndex() {
        return index;
    }

    public Integer getDimension() {
        return dimension;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getPodType() {
        return podType;
    }

    public Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String index;
        private Integer dimension;
        private String environment;
        private String podType;

        public Builder index(String index) {
            this.index = index;
            return this;
        }

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

        public PineconePodIndexParam build() {
            return new PineconePodIndexParam(index, dimension, environment, podType);
        }
    }
}
