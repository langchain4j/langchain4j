package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.dropTable;
import static dev.langchain4j.store.embedding.oracle.CommonTestOperations.newEmbeddingStoreBuilder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases which configure {@link OracleEmbeddingStore} with all possible {@link DistanceMetric} options and
 * exact/approximate search options.
 */
public class DistanceMetricTest {

    /** Verifies all distance metrics with approximate search */
    @ParameterizedTest
    @EnumSource(DistanceMetric.class)
    public void testDistanceMetricApproximate(DistanceMetric distanceMetric) throws SQLException {
        verifyDistanceMetric(distanceMetric, false);
    }

    /** Verifies all distance metrics with approximate search */
    @ParameterizedTest
    @EnumSource(DistanceMetric.class)
    public void testDistanceMetricExact(DistanceMetric distanceMetric) throws SQLException {
        verifyDistanceMetric(distanceMetric, true);
    }

    private void verifyDistanceMetric(DistanceMetric distanceMetric, boolean isExactSearch) throws SQLException  {

        OracleEmbeddingStore oracleEmbeddingStore =
                newEmbeddingStoreBuilder()
                   .distanceMetric(distanceMetric)
                   .exactSearch(isExactSearch)
                   .build();

        try {
            float[] vector0 = CommonTestOperations.randomFloats(512);
            float[] vector1 = vector0.clone();

            // Only higher indexes are increased in order to effect the cosine angle, and not just magnitude
            for (int i = 0; i < vector1.length / 2; i++)
                vector1[i] += 0.1f;

            List<Embedding> embeddings = new ArrayList<>(2);
            embeddings.add(Embedding.from(vector0));
            embeddings.add(Embedding.from(vector1));

            // Add the two vectors
            List<String> ids = oracleEmbeddingStore.addAll(embeddings);

            // Search for the first vector
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(Embedding.from(vector1))
                    .build();

            // Verify the first vector is matched
            EmbeddingMatch<TextSegment> match =
                oracleEmbeddingStore.search(request)
                        .matches()
                        .get(0);
            assertEquals(ids.get(1), match.embeddingId());
            assertArrayEquals(vector1, match.embedding().vector());
        }
        finally {
            dropTable();
        }
    }

}
