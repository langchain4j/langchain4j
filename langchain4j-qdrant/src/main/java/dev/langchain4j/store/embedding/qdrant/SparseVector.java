package dev.langchain4j.store.embedding.qdrant;

import java.util.List;

/**
 * A sparse vector represented as parallel lists of indices and values.
 */
public record SparseVector(List<Integer> indices, List<Float> values) {}
