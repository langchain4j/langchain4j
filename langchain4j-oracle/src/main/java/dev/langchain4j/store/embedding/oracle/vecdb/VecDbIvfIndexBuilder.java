package dev.langchain4j.store.embedding.oracle.vecdb;

/**
 * Builds an Inverted File Flat index configuration for VecDB.
 */
public final class VecDbIvfIndexBuilder extends VecDbIndexBuilder<VecDbIvfIndexBuilder> {

    Integer partitions;

    VecDbIvfIndexBuilder() {
        super(VecDbIndexOrganization.PARTITIONS);
    }

    /**
     * Configures the number of partitions used to divide the vector space.
     */
    public VecDbIvfIndexBuilder partitions(int partitions) {
        this.partitions = ensurePositive(partitions, "partitions");
        return this;
    }

    @Override
    protected VecDbIvfIndexBuilder self() {
        return this;
    }
}
