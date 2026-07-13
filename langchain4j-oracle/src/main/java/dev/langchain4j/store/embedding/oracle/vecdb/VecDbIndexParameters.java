package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;

/**
 * Root {@code index_params} configuration accepted by {@code DBMS_VECTOR_DATABASE}.
 *
 * <p>Vector- and metadata-index settings belong to their respective nested objects. Settings that apply to the
 * complete index operation, such as {@code parallel_creation}, are configured here.
 */
public final class VecDbIndexParameters {

    private final VecDbVectorIndex vectorIndex;
    private final VecDbMetadataIndex metadataIndex;
    private final Integer parallelCreation;

    private VecDbIndexParameters(Builder builder) {
        this.vectorIndex = builder.vectorIndex;
        this.metadataIndex = builder.metadataIndex;
        this.parallelCreation = builder.parallelCreation;
    }

    /** Returns a builder for the root VecDB index parameters. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the vector-index configuration, or {@code null} when it is not configured. */
    public VecDbVectorIndex vectorIndex() {
        return vectorIndex;
    }

    /** Returns the metadata-index configuration, or {@code null} when it is not configured. */
    public VecDbMetadataIndex metadataIndex() {
        return metadataIndex;
    }

    /** Returns the parallelism used for index creation, or {@code null} to use the database default. */
    public Integer parallelCreation() {
        return parallelCreation;
    }

    /** Builder for {@link VecDbIndexParameters}. */
    public static final class Builder {

        private VecDbVectorIndex vectorIndex;
        private VecDbMetadataIndex metadataIndex;
        private Integer parallelCreation;

        private Builder() {}

        /** Configures the nested {@code vector_index_params}. */
        public Builder vectorIndex(VecDbVectorIndex vectorIndex) {
            this.vectorIndex = vectorIndex;
            return this;
        }

        /** Configures the nested {@code metadata_index_params}. */
        public Builder metadataIndex(VecDbMetadataIndex metadataIndex) {
            this.metadataIndex = metadataIndex;
            return this;
        }

        /** Configures the root-level {@code parallel_creation} value. */
        public Builder parallelCreation(int parallelCreation) {
            this.parallelCreation = ensureGreaterThanZero(parallelCreation, "parallelCreation");
            return this;
        }

        /** Builds the root VecDB index parameters. */
        public VecDbIndexParameters build() {
            return new VecDbIndexParameters(this);
        }
    }
}
