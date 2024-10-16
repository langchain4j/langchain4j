package dev.langchain4j.store.embedding.qdrant;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.provider.Arguments;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;
import java.util.stream.Stream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.lang.reflect.Method;

import static dev.langchain4j.internal.Utils.randomUUID;

@Testcontainers
class QdrantEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static String collectionName = "langchain4j-" + randomUUID();
    private static int dimension = 384;
    private static Distance distance = Distance.Cosine;
    private static QdrantEmbeddingStore embeddingStore;

    @Container
    private static final QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:latest");

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void setup() throws InterruptedException, ExecutionException {
        embeddingStore = QdrantEmbeddingStore.builder()
            .host(qdrant.getHost())
            .port(qdrant.getGrpcPort())
            .collectionName(collectionName)
            .build();

        QdrantClient client = new QdrantClient(
            QdrantGrpcClient.newBuilder(qdrant.getHost(), qdrant.getGrpcPort(), false)
            .build());

        client
            .createCollectionAsync(
                collectionName,
                VectorParams.newBuilder().setDistance(distance).setSize(dimension)
                .build())
            .get();

        client.close();
    }

    @AfterAll
    static void teardown() {
        embeddingStore.close();
    }

    @Override
    protected EmbeddingStore < TextSegment > embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        embeddingStore.clearStore();
    }

    // - Eq, NEq, In, NIn don't allow float and double values. Only integers and
    // strings.
    // - LT, GT, LTE, GTE allow only numbers, not alphabets.
    // - For In and NIn conditions, if the key doesn't exist in the metadata, it is
    // not matched.

    protected static Stream < Arguments > should_filter_by_metadata_not() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata_not()
            .filter(arguments -> {
                Filter filter = (Filter) arguments.get()[0];
                if (filter instanceof IsNotIn notIn) {
                    try {
                        Method method = notIn.getClass().getMethod("key");
                        String key = (String) method.invoke(filter);

                        List < Metadata > matchingMetadatas = (List < Metadata > ) arguments.get()[1];
                        // For NIn conditions, if the key doesn't exist in the metadata it is not
                        // matched.
                        Boolean matching = matchingMetadatas.stream()
                            .allMatch(metadata -> metadata.containsKey(key));
                        if (!matching) {
                            return false;
                        }

                        Object firstValue = notIn.comparisonValues().stream().findFirst().get();
                        return firstValue instanceof String ||
                            firstValue instanceof UUID ||
                            firstValue instanceof Integer ||
                            firstValue instanceof Long;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else if (filter instanceof IsNotEqualTo notEqualTo) {
                    return notEqualTo.comparisonValue() instanceof String ||
                        notEqualTo.comparisonValue() instanceof UUID ||
                        notEqualTo.comparisonValue() instanceof Integer ||
                        notEqualTo.comparisonValue() instanceof Long;
                } else {
                    return true;
                }
            });
    }

    protected static Stream < Arguments > should_filter_by_metadata() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata()
            .filter(arguments -> {
                Filter filter = (Filter) arguments.get()[0];
                if (filter instanceof IsLessThan lessThan) {
                    return lessThan.comparisonValue() instanceof Integer ||
                        lessThan.comparisonValue() instanceof Long;
                } else if (filter instanceof IsLessThanOrEqualTo lessThanOrEqualTo) {
                    return lessThanOrEqualTo.comparisonValue() instanceof Integer ||
                        lessThanOrEqualTo.comparisonValue() instanceof Long;
                } else if (filter instanceof IsGreaterThan greaterThan) {
                    return greaterThan.comparisonValue() instanceof Integer ||
                        greaterThan.comparisonValue() instanceof Long;
                } else if (filter instanceof IsGreaterThanOrEqualTo greaterThanOrEqualTo) {
                    return greaterThanOrEqualTo.comparisonValue() instanceof Integer ||
                        greaterThanOrEqualTo.comparisonValue() instanceof Long;
                } else if (filter instanceof IsEqualTo equalTo) {
                    return equalTo.comparisonValue() instanceof String ||
                        equalTo.comparisonValue() instanceof UUID ||
                        equalTo.comparisonValue() instanceof Integer ||
                        equalTo.comparisonValue() instanceof Long;
                } else if (filter instanceof IsIn in) {
                    Object firstValue = in .comparisonValues().stream().findFirst().get();
                    return firstValue instanceof String ||
                        firstValue instanceof UUID ||
                        firstValue instanceof Integer ||
                        firstValue instanceof Long;
                } else {
                    return true;
                }
            });
    }
}