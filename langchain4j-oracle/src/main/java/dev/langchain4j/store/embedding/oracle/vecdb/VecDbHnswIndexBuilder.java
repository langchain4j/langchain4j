package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Builds a Hierarchical Navigable Small World index configuration for VecDB.
 */
public final class VecDbHnswIndexBuilder extends VecDbIndexBuilder<VecDbHnswIndexBuilder> {

    Integer neighbors;
    Integer efConstruction;
    Integer rescoreFactor;
    VecDbQuantizationAlgorithm quantizationAlgorithm;
    VecDbDistributeParameters distributeParameters;

    VecDbHnswIndexBuilder() {
        super(VecDbIndexOrganization.INMEMORY_NEIGHBOR_GRAPH);
    }

    /**
     * Configures the maximum number of connections per vector in the HNSW graph.
     */
    public VecDbHnswIndexBuilder neighbors(int neighbors) {
        this.neighbors = ensureBetween(neighbors, 1, 2048, "neighbors");
        return this;
    }

    /**
     * Configures the maximum number of candidates considered while constructing the HNSW graph.
     */
    public VecDbHnswIndexBuilder efConstruction(int efConstruction) {
        this.efConstruction = ensureBetween(efConstruction, 1, 65_535, "efConstruction");
        return this;
    }

    /** Configures the HNSW rescore factor. */
    public VecDbHnswIndexBuilder rescoreFactor(int rescoreFactor) {
        this.rescoreFactor = ensureBetween(rescoreFactor, 1, 100, "rescoreFactor");
        return this;
    }

    /** Configures the advanced HNSW quantization algorithm. */
    public VecDbHnswIndexBuilder quantizationAlgorithm(VecDbQuantizationAlgorithm quantizationAlgorithm) {
        this.quantizationAlgorithm = ensureNotNull(quantizationAlgorithm, "quantizationAlgorithm");
        return this;
    }

    /** Configures distributed HNSW index parameters. */
    public VecDbHnswIndexBuilder distributeParameters(VecDbDistributeParameters distributeParameters) {
        this.distributeParameters = ensureNotNull(distributeParameters, "distributeParameters");
        return this;
    }

    @Override
    protected VecDbHnswIndexBuilder self() {
        return this;
    }
}
