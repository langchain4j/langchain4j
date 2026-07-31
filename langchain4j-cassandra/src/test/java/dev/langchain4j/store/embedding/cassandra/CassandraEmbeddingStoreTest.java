package dev.langchain4j.store.embedding.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dtsx.astra.sdk.cassio.AnnQuery;
import com.dtsx.astra.sdk.cassio.CassandraSimilarityMetric;
import com.dtsx.astra.sdk.cassio.MetadataVectorTable;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CassandraEmbeddingStoreTest {

    private static final Embedding QUERY = Embedding.from(new float[] {1.0f, 2.0f, 3.0f});

    private CassandraSimilarityMetric capturedMetric(CassandraSimilarityMetric configured, boolean withMetadata) {
        MetadataVectorTable table = mock(MetadataVectorTable.class);
        when(table.getSimilarityMetric()).thenReturn(configured);
        when(table.similaritySearch(any())).thenReturn(List.of());

        CassandraEmbeddingStore store = mock(CassandraEmbeddingStore.class, CALLS_REAL_METHODS);
        store.embeddingTable = table;

        if (withMetadata) {
            store.findRelevant(QUERY, 5, 0.0, Metadata.from("k", "v"));
        } else {
            store.findRelevant(QUERY, 5, 0.0);
        }

        ArgumentCaptor<AnnQuery> captor = ArgumentCaptor.forClass(AnnQuery.class);
        org.mockito.Mockito.verify(table).similaritySearch(captor.capture());
        return captor.getValue().getMetric();
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
}
