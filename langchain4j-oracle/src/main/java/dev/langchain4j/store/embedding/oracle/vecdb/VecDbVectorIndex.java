package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.store.embedding.oracle.CreateOption;

/**
 * Configuration of an IVF or HNSW index managed by {@code DBMS_VECTOR_DATABASE}.
 *
 * <p>Use {@link #ivfIndexBuilder()} or {@link #hnswIndexBuilder()} to create an index configuration. The resulting
 * index can be passed to the {@code VecDbEmbeddingStore} builder.
 */
public final class VecDbVectorIndex {

    private final VecDbIndexOrganization organization;
    private final VecDbDistanceMetric distanceMetric;
    private final Integer accuracy;
    private final VecDbQuantizationType quantizationType;
    private final Integer compressionRatio;
    private final Boolean onlineBuild;
    private final VecDbDistributeParameters distributeParameters;
    private final Integer partitions;
    private final Integer neighbors;
    private final Integer efConstruction;
    private final Integer rescoreFactor;
    private final VecDbQuantizationAlgorithm quantizationAlgorithm;
    private final CreateOption createOption;

    VecDbVectorIndex(VecDbIndexBuilder<?> builder) {
        this.organization = builder.organization;
        this.distanceMetric = builder.distanceMetric;
        this.accuracy = builder.accuracy;
        this.quantizationType = builder.quantizationType;
        this.compressionRatio = builder.compressionRatio;
        this.onlineBuild = builder.onlineBuild;
        this.distributeParameters =
                builder instanceof VecDbHnswIndexBuilder hnswBuilder ? hnswBuilder.distributeParameters : null;
        this.partitions = builder instanceof VecDbIvfIndexBuilder ivfBuilder ? ivfBuilder.partitions : null;
        this.neighbors = builder instanceof VecDbHnswIndexBuilder hnswBuilder ? hnswBuilder.neighbors : null;
        this.efConstruction = builder instanceof VecDbHnswIndexBuilder hnswBuilder ? hnswBuilder.efConstruction : null;
        this.rescoreFactor = builder instanceof VecDbHnswIndexBuilder hnswBuilder ? hnswBuilder.rescoreFactor : null;
        this.quantizationAlgorithm =
                builder instanceof VecDbHnswIndexBuilder hnswBuilder ? hnswBuilder.quantizationAlgorithm : null;
        this.createOption = builder.createOption;
    }

    /** Returns a builder for an IVF index. */
    public static VecDbIvfIndexBuilder ivfIndexBuilder() {
        return new VecDbIvfIndexBuilder();
    }

    /** Returns a builder for an HNSW index. */
    public static VecDbHnswIndexBuilder hnswIndexBuilder() {
        return new VecDbHnswIndexBuilder();
    }

    public VecDbIndexOrganization organization() {
        return organization;
    }

    public VecDbDistanceMetric distanceMetric() {
        return distanceMetric;
    }

    public Integer accuracy() {
        return accuracy;
    }

    public VecDbQuantizationType quantizationType() {
        return quantizationType;
    }

    public Integer compressionRatio() {
        return compressionRatio;
    }

    public Boolean onlineBuild() {
        return onlineBuild;
    }

    public VecDbDistributeParameters distributeParameters() {
        return distributeParameters;
    }

    public Integer partitions() {
        return partitions;
    }

    public Integer neighbors() {
        return neighbors;
    }

    public Integer efConstruction() {
        return efConstruction;
    }

    public Integer rescoreFactor() {
        return rescoreFactor;
    }

    public VecDbQuantizationAlgorithm quantizationAlgorithm() {
        return quantizationAlgorithm;
    }

    public CreateOption createOption() {
        return createOption;
    }
}
