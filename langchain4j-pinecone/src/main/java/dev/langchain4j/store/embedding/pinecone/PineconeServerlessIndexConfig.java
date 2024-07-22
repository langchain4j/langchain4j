package dev.langchain4j.store.embedding.pinecone;

import io.pinecone.clients.Pinecone;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class PineconeServerlessIndexConfig implements PineconeIndexConfig {

    private final Integer dimension;
    private final String cloud;
    private final String region;
    private final String metric;

    PineconeServerlessIndexConfig(Integer dimension,
                                  String cloud,
                                  String region,
                                  String metric) {
        cloud = ensureNotNull(cloud, "cloud");
        region = ensureNotNull(region, "region");
        ensureNotNull(dimension, "dimension");

        this.dimension = dimension;
        this.cloud = cloud;
        this.region = region;
        this.metric = getOrDefault(metric, "cosine");
    }

    @Override
    public void createIndex(Pinecone pinecone, String index) {
        ensureNotNull(pinecone, "pinecone");
        ensureNotNull(index, "index");
        pinecone.createServerlessIndex(index, metric, dimension, cloud, region);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer dimension;
        private String cloud;
        private String region;
        private String metric;

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder cloud(String cloud) {
            this.cloud = cloud;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder metric(String metric) {
            this.metric = metric;
            return this;
        }

        public PineconeServerlessIndexConfig build() {
            return new PineconeServerlessIndexConfig(dimension, cloud, region, metric);
        }
    }
}
