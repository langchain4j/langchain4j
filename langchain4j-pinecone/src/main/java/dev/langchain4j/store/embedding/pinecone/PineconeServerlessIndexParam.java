package dev.langchain4j.store.embedding.pinecone;

import io.pinecone.clients.Pinecone;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class PineconeServerlessIndexParam implements PineconeIndexParam {

    private final String index;
    private final Integer dimension;
    private final String cloud;
    private final String region;
    private final String metrics;

    PineconeServerlessIndexParam(String index,
                                 Integer dimension,
                                 String cloud,
                                 String region,
                                 String metrics) {
        index = ensureNotNull(index, "index");
        cloud = ensureNotNull(cloud, "cloud");
        region = ensureNotNull(region, "region");
        ensureNotNull(dimension, "dimension");

        this.index = index;
        this.dimension = dimension;
        this.cloud = cloud;
        this.region = region;
        this.metrics = getOrDefault(metrics, "cosine");
    }

    @Override
    public void createIndex(Pinecone pinecone) {
        ensureNotNull(pinecone, "pinecone");
        pinecone.createServerlessIndex(index, metrics, dimension, cloud, region);
    }

    public String getIndex() {
        return index;
    }

    public Integer getDimension() {
        return dimension;
    }

    public String getCloud() {
        return cloud;
    }

    public String getRegion() {
        return region;
    }

    public String getMetrics() {
        return metrics;
    }

    public Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String index;
        private Integer dimension;
        private String cloud;
        private String region;
        private String metrics;

        public Builder index(String index) {
            this.index = index;
            return this;
        }

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

        public Builder metrics(String metrics) {
            this.metrics = metrics;
            return this;
        }

        public PineconeServerlessIndexParam build() {
            return new PineconeServerlessIndexParam(index, dimension, cloud, region, metrics);
        }
    }
}
