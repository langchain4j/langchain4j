package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;

/**
 * Builds a Hierarchical Navigable Small World index configuration for VecDB.
 */
public final class VecDbHnswIndexBuilder extends VecDbIndexBuilder<VecDbHnswIndexBuilder> {

    Integer neighbors;
    Integer efConstruction;

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

    @Override
    protected VecDbHnswIndexBuilder self() {
        return this;
    }
}
