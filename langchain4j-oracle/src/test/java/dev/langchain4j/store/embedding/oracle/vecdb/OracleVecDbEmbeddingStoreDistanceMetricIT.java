package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies distance-metric selection and LangChain4j score conversion against VecDB. */
@Testcontainers(disabledWithoutDocker = true)
class OracleVecDbEmbeddingStoreDistanceMetricIT {

    private static final String TABLE_NAME = "LC4J_VECDB_METRIC_IT";
    private static final double SCORE_TOLERANCE = 1e-5;

    /** Verifies score conversion for every distance metric exposed by the VecDB store. */
    @ParameterizedTest
    @MethodSource("explicitDistanceMetricCases")
    void testConvertsExplicitDistanceMetricToLangChainScore(
            VecDbDistanceMetric metric, Embedding query, Embedding stored, double expectedScore) {
        OracleVecDbEmbeddingStore embeddingStore = createStore(metric, null);
        embeddingStore.add("metric-vector", stored);

        EmbeddingMatch<?> match = search(embeddingStore, query, 0.0).matches().get(0);

        assertThat(match.score()).isCloseTo(expectedScore, within(SCORE_TOLERANCE));
    }

    static Stream<Arguments> explicitDistanceMetricCases() {
        return Stream.of(
                Arguments.of(VecDbDistanceMetric.COSINE, embedding(1.0f, 0.0f), embedding(0.0f, 1.0f), 0.5),
                Arguments.of(VecDbDistanceMetric.EUCLIDEAN, embedding(0.0f, 0.0f), embedding(3.0f, 4.0f), 1.0 / 6.0),
                Arguments.of(VecDbDistanceMetric.MANHATTAN, embedding(0.0f, 0.0f), embedding(2.0f, 3.0f), 1.0 / 6.0),
                Arguments.of(VecDbDistanceMetric.DOT, embedding(1.0f, 0.0f), embedding(0.25f, 0.0f), 0.25),
                Arguments.of(
                        VecDbDistanceMetric.EUCLIDEAN_SQUARED, embedding(0.0f, 0.0f), embedding(3.0f, 4.0f), 1.0 / 6.0),
                Arguments.of(VecDbDistanceMetric.L2_SQUARED, embedding(0.0f, 0.0f), embedding(3.0f, 4.0f), 1.0 / 6.0));
    }

    /** Verifies Oracle's cosine fallback when neither the store nor a vector index specifies a metric. */
    @Test
    void testUsesCosineWhenSearchAndIndexMetricsAreOmitted() {
        OracleVecDbEmbeddingStore embeddingStore = createStore(null, null);
        embeddingStore.add("orthogonal-vector", embedding(0.0f, 1.0f));

        EmbeddingMatch<?> match =
                search(embeddingStore, embedding(1.0f, 0.0f), 0.0).matches().get(0);

        assertThat(match.score()).isCloseTo(0.5, within(SCORE_TOLERANCE));
    }

    /** Verifies that an omitted search metric uses the metric configured on the vector index. */
    @Test
    void testUsesVectorIndexMetricWhenSearchMetricIsOmitted() {
        VecDbVectorIndex index = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                .distanceMetric(VecDbDistanceMetric.EUCLIDEAN)
                .build();
        OracleVecDbEmbeddingStore embeddingStore = createStore(null, index);
        embeddingStore.add("euclidean-vector", embedding(4.0f, 4.0f));

        EmbeddingMatch<?> match =
                search(embeddingStore, embedding(1.0f, 0.0f), 0.0).matches().get(0);

        assertThat(match.score()).isCloseTo(1.0 / 6.0, within(SCORE_TOLERANCE));
    }

    /** Verifies that {@code minScore} is applied after converting VecDB cosine distances. */
    @Test
    void testAppliesMinimumScoreAfterDistanceConversion() {
        OracleVecDbEmbeddingStore embeddingStore = createStore(VecDbDistanceMetric.COSINE, null);
        embeddingStore.add("identical-vector", embedding(1.0f, 0.0f));
        embeddingStore.add("orthogonal-vector", embedding(0.0f, 1.0f));

        assertThat(search(embeddingStore, embedding(1.0f, 0.0f), 0.75).matches())
                .extracting(EmbeddingMatch::embeddingId)
                .containsExactly("identical-vector");
    }

    @AfterEach
    void dropVectorTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }

    private static OracleVecDbEmbeddingStore createStore(
            VecDbDistanceMetric searchMetric, VecDbVectorIndex vectorIndex) {
        OracleVecDbEmbeddingStore.Builder builder = OracleVecDbEmbeddingStore.builder()
                .dataSource(VecDbTestOperations.dataSource())
                .embeddingTable(TABLE_NAME, CreateOption.CREATE_OR_REPLACE);
        if (searchMetric != null) {
            builder.distanceMetric(searchMetric);
        }
        if (vectorIndex != null) {
            builder.index(vectorIndex);
        }
        return builder.build();
    }

    private static EmbeddingSearchResult<TextSegment> search(
            OracleVecDbEmbeddingStore embeddingStore, Embedding query, double minScore) {
        return embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(query)
                .maxResults(10)
                .minScore(minScore)
                .build());
    }

    private static Embedding embedding(float... values) {
        return new Embedding(values);
    }
}
