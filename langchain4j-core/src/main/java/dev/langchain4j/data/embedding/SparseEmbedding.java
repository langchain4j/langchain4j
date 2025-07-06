package dev.langchain4j.data.embedding;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class SparseEmbedding {
    private final List<Long> indices;
    private final List<Float> values;

    public SparseEmbedding(List<Long> indices, List<Float> values) {
        if (indices.size() != values.size()) {
            throw new IllegalArgumentException("the length of indices and values must be the same");
        }
        this.indices = indices;
        this.values = values;
    }

    public List<Long> getIndices() {
        return indices;
    }

    public List<Float> getValues() {
        return values;
    }

    public SortedMap<Long, Float> vectorAsSortedMap() {
        SortedMap<Long, Float> vector = new TreeMap<>();
        for (int i = 0; i < indices.size(); i++) {
            vector.put(indices.get(i), values.get(i));
        }
        return vector;
    }
}
