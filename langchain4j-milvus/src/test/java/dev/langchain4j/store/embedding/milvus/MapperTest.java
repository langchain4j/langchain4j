package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.store.embedding.milvus.Mapper.toSparseVectors;
import static dev.langchain4j.store.embedding.milvus.Mapper.toVectors;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.embedding.SparseEmbedding;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class MapperTest implements WithAssertions {

    @Test
    void should_convert_embeddings_to_vectors() {
        // given
        List<Embedding> embeddings = Arrays.asList(
                Embedding.from(Arrays.asList(1.0f, 2.0f, 3.0f)), Embedding.from(Arrays.asList(4.0f, 5.0f, 6.0f)));

        // when
        List<List<Float>> vectors = toVectors(embeddings);

        // then
        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(vectors.get(1)).containsExactly(4.0f, 5.0f, 6.0f);
    }

    @Test
    void should_convert_sparse_embeddings_to_sparse_vectors() {
        // given
        List<SparseEmbedding> sparseEmbeddings = Arrays.asList(
                new SparseEmbedding(Arrays.asList(1L, 3L, 5L), Arrays.asList(0.1f, 0.3f, 0.5f)),
                new SparseEmbedding(Arrays.asList(2L, 4L), Arrays.asList(0.2f, 0.4f)));

        // when
        List<SortedMap<Long, Float>> sparseVectors = toSparseVectors(sparseEmbeddings);

        // then
        assertThat(sparseVectors).hasSize(2);

        SortedMap<Long, Float> firstVector = sparseVectors.get(0);
        assertThat(firstVector).hasSize(3);
        assertThat(firstVector.get(1L)).isEqualTo(0.1f);
        assertThat(firstVector.get(3L)).isEqualTo(0.3f);
        assertThat(firstVector.get(5L)).isEqualTo(0.5f);
        assertThat(firstVector.keySet()).containsExactly(1L, 3L, 5L);

        SortedMap<Long, Float> secondVector = sparseVectors.get(1);
        assertThat(secondVector).hasSize(2);
        assertThat(secondVector.get(2L)).isEqualTo(0.2f);
        assertThat(secondVector.get(4L)).isEqualTo(0.4f);
        assertThat(secondVector.keySet()).containsExactly(2L, 4L);
    }

    @Test
    void should_handle_empty_sparse_embeddings_list() {
        // given
        List<SparseEmbedding> sparseEmbeddings = Arrays.asList();

        // when
        List<SortedMap<Long, Float>> sparseVectors = toSparseVectors(sparseEmbeddings);

        // then
        assertThat(sparseVectors).isEmpty();
    }

    @Test
    void should_handle_empty_sparse_embedding() {
        // given
        List<SparseEmbedding> sparseEmbeddings = Arrays.asList(new SparseEmbedding(Arrays.asList(), Arrays.asList()));

        // when
        List<SortedMap<Long, Float>> sparseVectors = toSparseVectors(sparseEmbeddings);

        // then
        assertThat(sparseVectors).hasSize(1);
        assertThat(sparseVectors.get(0)).isEmpty();
    }

    @Test
    void should_handle_unsorted_indices_in_sparse_embedding() {
        // given
        List<SparseEmbedding> sparseEmbeddings =
                Arrays.asList(new SparseEmbedding(Arrays.asList(5L, 1L, 3L), Arrays.asList(0.5f, 0.1f, 0.3f)));

        // when
        List<SortedMap<Long, Float>> sparseVectors = toSparseVectors(sparseEmbeddings);

        // then
        assertThat(sparseVectors).hasSize(1);
        SortedMap<Long, Float> vector = sparseVectors.get(0);
        assertThat(vector).hasSize(3);
        assertThat(vector.keySet()).containsExactly(1L, 3L, 5L); // Should be sorted
        assertThat(vector.get(1L)).isEqualTo(0.1f);
        assertThat(vector.get(3L)).isEqualTo(0.3f);
        assertThat(vector.get(5L)).isEqualTo(0.5f);
    }

    @Test
    void should_handle_duplicate_indices_in_sparse_embedding() {
        // given
        List<SparseEmbedding> sparseEmbeddings =
                Arrays.asList(new SparseEmbedding(Arrays.asList(1L, 1L, 2L), Arrays.asList(0.1f, 0.2f, 0.3f)));

        // when
        List<SortedMap<Long, Float>> sparseVectors = toSparseVectors(sparseEmbeddings);

        // then
        assertThat(sparseVectors).hasSize(1);
        SortedMap<Long, Float> vector = sparseVectors.get(0);
        assertThat(vector).hasSize(2); // TreeMap overwrites duplicate keys
        assertThat(vector.get(1L)).isEqualTo(0.2f); // Last value for key 1L
        assertThat(vector.get(2L)).isEqualTo(0.3f);
    }

    @Test
    void should_handle_negative_indices_in_sparse_embedding() {
        // given
        List<SparseEmbedding> sparseEmbeddings =
                Arrays.asList(new SparseEmbedding(Arrays.asList(1L, -1L, 0L), Arrays.asList(0.1f, -0.1f, 0.0f)));

        // when
        List<SortedMap<Long, Float>> sparseVectors = toSparseVectors(sparseEmbeddings);

        // then
        assertThat(sparseVectors).hasSize(1);
        SortedMap<Long, Float> vector = sparseVectors.get(0);
        assertThat(vector).hasSize(3);
        assertThat(vector.keySet()).containsExactly(-1L, 0L, 1L); // Should be sorted
        assertThat(vector.get(-1L)).isEqualTo(-0.1f);
        assertThat(vector.get(0L)).isEqualTo(0.0f);
        assertThat(vector.get(1L)).isEqualTo(0.1f);
    }
}
