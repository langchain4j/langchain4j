package dev.langchain4j.store.embedding.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dtsx.astra.sdk.cassio.AnnQuery;
import com.dtsx.astra.sdk.cassio.AnnResult;
import com.dtsx.astra.sdk.cassio.CassandraSimilarityMetric;
import com.dtsx.astra.sdk.cassio.MetadataVectorRecord;
import com.dtsx.astra.sdk.cassio.MetadataVectorTable;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CassandraEmbeddingStoreTest {

    private static final Embedding QUERY = Embedding.from(new float[] {1.0f, 2.0f, 3.0f});

    private CassandraEmbeddingStore storeBackedBy(MetadataVectorTable table) {
        CassandraEmbeddingStore store = mock(CassandraEmbeddingStore.class, CALLS_REAL_METHODS);
        store.embeddingTable = table;
        return store;
    }

    private AnnQuery capturedQuery(CassandraSimilarityMetric configured, double minScore, boolean withMetadata) {
        MetadataVectorTable table = mock(MetadataVectorTable.class);
        when(table.getSimilarityMetric()).thenReturn(configured);
        when(table.similaritySearch(any())).thenReturn(List.of());

        CassandraEmbeddingStore store = storeBackedBy(table);
        if (withMetadata) {
            store.findRelevant(QUERY, 5, minScore, Metadata.from("k", "v"));
        } else {
            store.findRelevant(QUERY, 5, minScore);
        }

        ArgumentCaptor<AnnQuery> captor = ArgumentCaptor.forClass(AnnQuery.class);
        org.mockito.Mockito.verify(table).similaritySearch(captor.capture());
        return captor.getValue();
    }

    private CassandraSimilarityMetric capturedMetric(CassandraSimilarityMetric configured, boolean withMetadata) {
        return capturedQuery(configured, 0.0, withMetadata).getMetric();
    }

    /**
     * Runs a search returning a single result whose CQL similarity is the given value.
     */
    private double scoreOf(float cqlSimilarity, boolean withMetadata) {
        MetadataVectorRecord record = new MetadataVectorRecord("row-1", List.of(1.0f, 2.0f, 3.0f));
        AnnResult<MetadataVectorRecord> result = new AnnResult<>();
        result.setEmbedded(record);
        result.setSimilarity(cqlSimilarity);

        MetadataVectorTable table = mock(MetadataVectorTable.class);
        when(table.getSimilarityMetric()).thenReturn(CassandraSimilarityMetric.COSINE);
        when(table.similaritySearch(any())).thenReturn(List.of(result));

        CassandraEmbeddingStore store = storeBackedBy(table);
        List<EmbeddingMatch<TextSegment>> matches = withMetadata
                ? store.findRelevant(QUERY, 5, 0.0, Metadata.from("k", "v"))
                : store.findRelevant(QUERY, 5, 0.0);

        assertThat(matches).hasSize(1);
        return matches.get(0).score();
    }

    @Test
    void findRelevant_uses_configured_metric() {
        assertThat(capturedMetric(CassandraSimilarityMetric.EUCLIDEAN, false))
                .isEqualTo(CassandraSimilarityMetric.EUCLIDEAN);
    }

    @Test
    void findRelevant_with_metadata_uses_configured_metric() {
        assertThat(capturedMetric(CassandraSimilarityMetric.EUCLIDEAN, true))
                .isEqualTo(CassandraSimilarityMetric.EUCLIDEAN);
    }

    @Test
    void findRelevant_defaults_to_cosine_when_configured_cosine() {
        assertThat(capturedMetric(CassandraSimilarityMetric.COSINE, false)).isEqualTo(CassandraSimilarityMetric.COSINE);
    }

    @Test
    void findRelevant_returns_cql_similarity_as_score() {
        // 1.0 = identical direction (relevant), 0.5 = orthogonal (not relevant)
        assertThat(scoreOf(1.0f, false)).isEqualTo(1.0);
        assertThat(scoreOf(0.5f, false)).isEqualTo(0.5);
    }

    @Test
    void findRelevant_with_metadata_returns_cql_similarity_as_score() {
        assertThat(scoreOf(1.0f, true)).isEqualTo(1.0);
        assertThat(scoreOf(0.5f, true)).isEqualTo(0.5);
    }

    @Test
    void findRelevant_passes_min_score_as_threshold() {
        assertThat(capturedQuery(CassandraSimilarityMetric.COSINE, 0.75, false).getThreshold())
                .isEqualTo(0.75);
    }

    @Test
    void findRelevant_with_metadata_passes_min_score_as_threshold() {
        assertThat(capturedQuery(CassandraSimilarityMetric.COSINE, 0.75, true).getThreshold())
                .isEqualTo(0.75);
    }
}
