package dev.langchain4j.store.embedding.milvus.v2;

import java.util.Arrays;
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

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof SparseEmbedding that)) return false;
        return Arrays.equals(this.indices, that.indices) && Arrays.equals(this.values, that.values);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(indices) + Arrays.hashCode(values);
    }
}
