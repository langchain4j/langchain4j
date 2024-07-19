package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases which configure {@link OracleEmbeddingStore} with all possible {@link DistanceMetric} options and
 * exact/approximate search options.
 */
public class DistanceMetricTest {

    private static final String SEED_PROPERTY = DistanceMetric.class.getSimpleName() + ".SEED";
    private static final long SEED = Long.getLong(SEED_PROPERTY, System.currentTimeMillis());

    /** Verifies all distance metrics with approximate search */
    @ParameterizedTest
    @EnumSource(DistanceMetric.class)
    public void testDistanceMetricApproximate(DistanceMetric distanceMetric)  {
        verifyDistanceMetric(distanceMetric, false);
    }

    /** Verifies all distance metrics with approximate search */
    @ParameterizedTest
    @EnumSource(DistanceMetric.class)
    public void testDistanceMetricExact(DistanceMetric distanceMetric)  {
        verifyDistanceMetric(distanceMetric, true);
    }

    private void verifyDistanceMetric(DistanceMetric distanceMetric, boolean isExactSearch)  {

        OracleEmbeddingStore oracleEmbeddingStore =
           OracleEmbeddingStore.builder()
                   .dataSource(CommonTestOperations.getDataSource())
                   .distanceMetric(distanceMetric)
                   .exactSearch(isExactSearch)
                   .tableName(getClass().getSimpleName() + "_" + distanceMetric.name())
                   .build();
        try {
            Random random = new Random(SEED);

            float[] vector0 = new float[1024];
            for (int i = 0; i < vector0.length; i++)
                vector0[i] = random.nextFloat();

            float[] vector1 = vector0.clone();
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
        catch (Exception exception) {
            throw new AssertionError(
                    "Test failed with random seed of " + SEED
                            + ". Run again with \"-D" + SEED_PROPERTY + "=" + SEED +
                            "\" to reproduce the failure.",
                    exception);
        }
        finally {
            // Clean up data
            oracleEmbeddingStore.removeAll();
        }
    }

}
