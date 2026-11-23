package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/** Distribution configuration for an HNSW vector index. */
public final class VecDbDistributeParameters {

    private final String distributeMethod;
    private final String serviceName;

    private VecDbDistributeParameters(Builder builder) {
        this.distributeMethod = builder.distributeMethod;
        this.serviceName = builder.serviceName;
    }

    /** Returns a builder for distributed HNSW parameters. */
    public static Builder builder() {
        return new Builder();
    }

    public String distributeMethod() {
        return distributeMethod;
    }

    public String serviceName() {
        return serviceName;
    }

    /** Builder for {@link VecDbDistributeParameters}. */
    public static final class Builder {

        private String distributeMethod;
        private String serviceName;

        private Builder() {}

        /** Configures the database distribution method. */
        public Builder distributeMethod(String distributeMethod) {
            this.distributeMethod = ensureNotBlank(distributeMethod, "distributeMethod");
            return this;
        }

        /** Configures the database service used by the distributed index. */
        public Builder serviceName(String serviceName) {
            this.serviceName = ensureNotBlank(serviceName, "serviceName");
            return this;
        }

        /** Builds the distribution parameters. */
        public VecDbDistributeParameters build() {
            if (distributeMethod == null && serviceName == null) {
                throw new IllegalArgumentException("At least one distributed HNSW parameter must be configured");
            }
            return new VecDbDistributeParameters(this);
        }
    }
}
