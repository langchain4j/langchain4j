package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.store.embedding.oracle.CreateOption;

/**
 * Configuration of an IVF or HNSW index managed by {@code DBMS_VECTOR_DATABASE}.
 *
 * <p>Use {@link #ivfIndexBuilder()} or {@link #hnswIndexBuilder()} to create an index configuration. The resulting
 * index can be passed to the {@code VecDbEmbeddingStore} builder.
 */
public final class VecDbIndex {

    private final VecDbIndexOrganization organization;
    private final VecDbDistanceMetric distanceMetric;
    private final Integer accuracy;
    private final Integer partitions;
    private final Integer neighbors;
    private final Integer efConstruction;
    private final Integer parallelCreation;
    private final CreateOption createOption;

    VecDbIndex(VecDbIndexBuilder<?> builder) {
        this.organization = builder.organization;
        this.distanceMetric = builder.distanceMetric;
        this.accuracy = builder.accuracy;
        this.partitions = builder instanceof VecDbIvfIndexBuilder ivfBuilder ? ivfBuilder.partitions : null;
        this.neighbors = builder instanceof VecDbHnswIndexBuilder hnswBuilder ? hnswBuilder.neighbors : null;
        this.efConstruction =
                builder instanceof VecDbHnswIndexBuilder hnswBuilder ? hnswBuilder.efConstruction : null;
        this.parallelCreation = builder.parallelCreation;
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

    public Integer partitions() {
        return partitions;
    }

    public Integer neighbors() {
        return neighbors;
    }

    public Integer efConstruction() {
        return efConstruction;
    }

    public Integer parallelCreation() {
        return parallelCreation;
    }

    public CreateOption createOption() {
        return createOption;
    }
}
