package dev.langchain4j.store.embedding.hibernate.milvus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class MilvusDatabaseKindTest {

    @Test
    void should_declare_the_metric_of_the_distance_function() {
        for (boolean createIndex : new boolean[] {false, true}) {
            assertThat(tableIndexOptions(createIndex, DistanceFunction.COSINE, null, null))
                    .isEqualTo("metric=cosine");
            assertThat(tableIndexOptions(createIndex, DistanceFunction.INNER_PRODUCT, null, null))
                    .isEqualTo("metric=ip");
            assertThat(tableIndexOptions(createIndex, DistanceFunction.NEGATIVE_INNER_PRODUCT, null, null))
                    .isEqualTo("metric=ip");
            assertThat(tableIndexOptions(createIndex, DistanceFunction.EUCLIDEAN, null, null))
                    .isEqualTo("metric=l2");
        }
    }

    @Test
    void should_add_the_index_type_and_the_index_options_with_create_index() {
        assertThat(tableIndexOptions(true, DistanceFunction.COSINE, "HNSW", "M=16,efConstruction=64"))
                .isEqualTo("metric=cosine,type=HNSW,M=16,efConstruction=64");
    }

    @Test
    void should_ignore_the_index_type_and_the_index_options_without_create_index() {
        assertThat(tableIndexOptions(false, DistanceFunction.COSINE, "HNSW", "M=16,efConstruction=64"))
                .isEqualTo("metric=cosine");
    }

    @Test
    void should_fail_at_build_for_a_distance_function_that_milvus_does_not_support() {
        List<DistanceFunction> unsupported = List.of(
                DistanceFunction.EUCLIDEAN_SQUARED,
                DistanceFunction.MANHATTAN,
                DistanceFunction.HAMMING,
                DistanceFunction.JACCARD);

        for (DistanceFunction distanceFunction : unsupported) {
            // fails before connecting, so no Milvus server is needed
            assertThatThrownBy(() -> HibernateEmbeddingStore.dynamicBuilder()
                            .databaseKind(MilvusDatabaseKind.INSTANCE)
                            .host("localhost")
                            .port(19530)
                            .database("default")
                            .table("test")
                            .dimension(3)
                            .distanceFunction(distanceFunction)
                            .build())
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Milvus does not support the distance function: " + distanceFunction);
        }
    }

    private static String tableIndexOptions(
            boolean createIndex, DistanceFunction distanceFunction, String indexType, String indexOptions) {
        return MilvusDatabaseKind.INSTANCE.tableIndexOptions(
                createIndex, distanceFunction, indexType, "test", "embedding", indexOptions);
    }
}
