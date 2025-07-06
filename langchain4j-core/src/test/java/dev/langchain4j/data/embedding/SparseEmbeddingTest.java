package dev.langchain4j.data.embedding;

import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class SparseEmbeddingTest implements WithAssertions {

    @Test
    void should_create_sparse_embedding() {
        // given
        List<Long> indices = Arrays.asList(1L, 3L, 5L);
        List<Float> values = Arrays.asList(0.1f, 0.3f, 0.5f);

        // when
        SparseEmbedding sparseEmbedding = new SparseEmbedding(indices, values);

        // then
        assertThat(sparseEmbedding.getIndices()).isEqualTo(indices);
        assertThat(sparseEmbedding.getValues()).isEqualTo(values);
    }

    @Test
    void should_throw_exception_when_indices_and_values_have_different_sizes() {
        // given
        List<Long> indices = Arrays.asList(1L, 3L);
        List<Float> values = Arrays.asList(0.1f, 0.3f, 0.5f);

        // when & then
        assertThatThrownBy(() -> new SparseEmbedding(indices, values))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the length of indices and values must be the same");
    }

    @Test
    void should_throw_exception_when_indices_and_values_have_different_sizes_reversed() {
        // given
        List<Long> indices = Arrays.asList(1L, 3L, 5L);
        List<Float> values = Arrays.asList(0.1f, 0.3f);

        // when & then
        assertThatThrownBy(() -> new SparseEmbedding(indices, values))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the length of indices and values must be the same");
    }

    @Test
    void should_create_sorted_map_from_sparse_embedding() {
        // given
        List<Long> indices = Arrays.asList(5L, 1L, 3L);
        List<Float> values = Arrays.asList(0.5f, 0.1f, 0.3f);
        SparseEmbedding sparseEmbedding = new SparseEmbedding(indices, values);

        // when
        SortedMap<Long, Float> sortedMap = sparseEmbedding.vectorAsSortedMap();

        // then
        assertThat(sortedMap).hasSize(3);
        assertThat(sortedMap.get(1L)).isEqualTo(0.1f);
        assertThat(sortedMap.get(3L)).isEqualTo(0.3f);
        assertThat(sortedMap.get(5L)).isEqualTo(0.5f);

        // Verify the map is sorted by keys
        assertThat(sortedMap.keySet()).containsExactly(1L, 3L, 5L);
    }

    @Test
    void should_handle_empty_sparse_embedding() {
        // given
        List<Long> indices = Arrays.asList();
        List<Float> values = Arrays.asList();
        SparseEmbedding sparseEmbedding = new SparseEmbedding(indices, values);

        // when
        SortedMap<Long, Float> sortedMap = sparseEmbedding.vectorAsSortedMap();

        // then
        assertThat(sortedMap).isEmpty();
        assertThat(sparseEmbedding.getIndices()).isEmpty();
        assertThat(sparseEmbedding.getValues()).isEmpty();
    }

    @Test
    void should_handle_single_element_sparse_embedding() {
        // given
        List<Long> indices = Arrays.asList(42L);
        List<Float> values = Arrays.asList(0.42f);
        SparseEmbedding sparseEmbedding = new SparseEmbedding(indices, values);

        // when
        SortedMap<Long, Float> sortedMap = sparseEmbedding.vectorAsSortedMap();

        // then
        assertThat(sortedMap).hasSize(1);
        assertThat(sortedMap.get(42L)).isEqualTo(0.42f);
    }

    @Test
    void should_handle_duplicate_indices() {
        // given
        List<Long> indices = Arrays.asList(1L, 1L, 2L);
        List<Float> values = Arrays.asList(0.1f, 0.2f, 0.3f);
        SparseEmbedding sparseEmbedding = new SparseEmbedding(indices, values);

        // when
        SortedMap<Long, Float> sortedMap = sparseEmbedding.vectorAsSortedMap();

        // then
        assertThat(sortedMap).hasSize(2); // TreeMap will overwrite duplicate keys
        assertThat(sortedMap.get(1L)).isEqualTo(0.2f); // Last value for key 1L
        assertThat(sortedMap.get(2L)).isEqualTo(0.3f);
    }

    @Test
    void should_handle_negative_indices() {
        // given
        List<Long> indices = Arrays.asList(-1L, 0L, 1L);
        List<Float> values = Arrays.asList(-0.1f, 0.0f, 0.1f);
        SparseEmbedding sparseEmbedding = new SparseEmbedding(indices, values);

        // when
        SortedMap<Long, Float> sortedMap = sparseEmbedding.vectorAsSortedMap();

        // then
        assertThat(sortedMap).hasSize(3);
        assertThat(sortedMap.get(-1L)).isEqualTo(-0.1f);
        assertThat(sortedMap.get(0L)).isEqualTo(0.0f);
        assertThat(sortedMap.get(1L)).isEqualTo(0.1f);

        // Verify the map is sorted by keys (negative numbers first)
        assertThat(sortedMap.keySet()).containsExactly(-1L, 0L, 1L);
    }

    @Test
    void should_handle_large_indices() {
        // given
        List<Long> indices = Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE, 0L);
        List<Float> values = Arrays.asList(1.0f, -1.0f, 0.0f);
        SparseEmbedding sparseEmbedding = new SparseEmbedding(indices, values);

        // when
        SortedMap<Long, Float> sortedMap = sparseEmbedding.vectorAsSortedMap();

        // then
        assertThat(sortedMap).hasSize(3);
        assertThat(sortedMap.get(Long.MIN_VALUE)).isEqualTo(-1.0f);
        assertThat(sortedMap.get(0L)).isEqualTo(0.0f);
        assertThat(sortedMap.get(Long.MAX_VALUE)).isEqualTo(1.0f);

        // Verify the map is sorted by keys
        assertThat(sortedMap.keySet()).containsExactly(Long.MIN_VALUE, 0L, Long.MAX_VALUE);
    }
}
