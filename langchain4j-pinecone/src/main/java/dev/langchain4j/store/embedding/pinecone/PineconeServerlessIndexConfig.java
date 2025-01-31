package dev.langchain4j.store.embedding.pinecone;

import io.pinecone.clients.Pinecone;
import org.openapitools.db_control.client.model.DeletionProtection;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class PineconeServerlessIndexConfig implements PineconeIndexConfig {

    private final Integer dimension;
    private final String cloud;
    private final String region;
    private final DeletionProtection deletionProtection;

    PineconeServerlessIndexConfig(Integer dimension,
                                  String cloud,
                                  String region) {
        this(
                dimension,
                cloud,
                region,
                DeletionProtection.ENABLED
        );
    }

    PineconeServerlessIndexConfig(Integer dimension,
                                  String cloud,
                                  String region,
                                  DeletionProtection deletionProtection){

        cloud = ensureNotNull(cloud, "cloud");
        region = ensureNotNull(region, "region");
        ensureNotNull(dimension, "dimension");

        this.dimension = dimension;
        this.cloud = cloud;
        this.region = region;
        this.deletionProtection = deletionProtection;
    }

    @Override
    public void createIndex(Pinecone pinecone, String index) {
        ensureNotNull(pinecone, "pinecone");
        ensureNotNull(index, "index");
        pinecone.createServerlessIndex(index, "cosine", dimension, cloud, region, deletionProtection);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer dimension;
        private String cloud;
        private String region;
        private DeletionProtection deletionProtection;

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

        public Builder deletionProtection(DeletionProtection deletionProtection) {
            this.deletionProtection = deletionProtection;
            return this;
        }

        public PineconeServerlessIndexConfig build() {
            if (deletionProtection == null) {
                return new PineconeServerlessIndexConfig(dimension, cloud, region);
            }
            return new PineconeServerlessIndexConfig(dimension, cloud, region, deletionProtection);
        }
    }
}
