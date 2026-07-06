package dev.langchain4j.store.embedding.oracle.vecdb;

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
        this.neighbors = ensurePositive(neighbors, "neighbors");
        return this;
    }

    /**
     * Configures the maximum number of candidates considered while constructing the HNSW graph.
     */
    public VecDbHnswIndexBuilder efConstruction(int efConstruction) {
        this.efConstruction = ensurePositive(efConstruction, "efConstruction");
        return this;
    }

    @Override
    protected VecDbHnswIndexBuilder self() {
        return this;
    }
}
