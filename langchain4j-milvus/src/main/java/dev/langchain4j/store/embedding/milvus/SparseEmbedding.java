package dev.langchain4j.store.embedding.milvus;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class SparseEmbedding {
    private final long[] indices;
    private final float[] values;

    public SparseEmbedding(long[] indices, float[] values) {
        if (indices.length != values.length) {
            throw new IllegalArgumentException("the length of indices and values must be the same");
        }
        this.indices = indices;
        this.values = values;
    }

    public long[] getIndices() {
        return indices;
    }

    public float[] getValues() {
        return values;
    }

    public SortedMap<Long, Float> vectorAsSortedMap() {
        SortedMap<Long, Float> vector = new TreeMap<>();
        for (int i = 0; i < indices.length; i++) {
            vector.put(indices[i], values[i]);
        }
        return vector;
    }
}
