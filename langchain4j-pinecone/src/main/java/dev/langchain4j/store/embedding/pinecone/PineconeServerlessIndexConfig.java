package dev.langchain4j.store.embedding.pinecone;

import io.pinecone.clients.Pinecone;
import org.openapitools.db_control.client.model.DeletionProtection;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static org.openapitools.db_control.client.model.DeletionProtection.ENABLED;

public class PineconeServerlessIndexConfig implements PineconeIndexConfig {

    private final Integer dimension;
    private final String cloud;
    private final String region;
    private final DeletionProtection deletionProtection;

    PineconeServerlessIndexConfig(Integer dimension,
                                  String cloud,
                                  String region,
                                  DeletionProtection deletionProtection) {
        this.dimension = ensureNotNull(dimension, "dimension");
        this.cloud = ensureNotBlank(cloud, "cloud");
        this.region = ensureNotBlank(region, "region");
        this.deletionProtection = getOrDefault(deletionProtection, ENABLED);
    }

    /**
     * @deprecated please use {@link #PineconeServerlessIndexConfig(Integer, String, String, DeletionProtection)} instead
     */
    @Deprecated(since = "1.0.0-beta1", forRemoval = true)
    PineconeServerlessIndexConfig(Integer dimension,
                                  String cloud,
                                  String region) {
        this(dimension, cloud, region, null);
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
            return new PineconeServerlessIndexConfig(dimension, cloud, region, deletionProtection);
        }
    }
}
