package dev.langchain4j.store.embedding.oracle.vecdb;

/**
 * Vector index organizations supported by {@code DBMS_VECTOR_DATABASE.CREATE_INDEX}.
 */
public enum VecDbIndexOrganization {

    /** Inverted File Flat index. */
    PARTITIONS,

    /** Hierarchical Navigable Small World index. */
    INMEMORY_NEIGHBOR_GRAPH
}
