package dev.langchain4j.store.embedding.qdrant;

import static dev.langchain4j.internal.Utils.randomUUID;
import static io.qdrant.client.grpc.Collections.Distance.Cosine;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.VectorParams;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

@Testcontainers
class QdrantEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static final String COLLECTION_NAME = "langchain4j-" + randomUUID();

    @Container
    private static final QdrantContainer QDRANT_CONTAINER = new QdrantContainer("qdrant/qdrant:latest");

    private static QdrantEmbeddingStore EMBEDDING_STORE;
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void setup() throws InterruptedException, ExecutionException {
        EMBEDDING_STORE = QdrantEmbeddingStore.builder()
                .host(QDRANT_CONTAINER.getHost())
                .port(QDRANT_CONTAINER.getGrpcPort())
                .collectionName(COLLECTION_NAME)
                .build();

        QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(QDRANT_CONTAINER.getHost(), QDRANT_CONTAINER.getGrpcPort(), false)
                        .build());

        client.createCollectionAsync(
                        COLLECTION_NAME,
                        VectorParams.newBuilder()
                                .setDistance(Cosine)
                                .setSize(EMBEDDING_MODEL.dimension())
                                .build())
                .get();

        client.close();
    }

    @AfterAll
    static void teardown() {
        EMBEDDING_STORE.close();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return EMBEDDING_STORE;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL;
    }

    @Override
    protected void clearStore() {
        EMBEDDING_STORE.clearStore();
    }

    @Override
    @ParameterizedTest
    @MethodSource("should_filter_by_metadata_not_qdrant")
    protected void should_filter_by_metadata_not(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata_not(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    // - Eq, NEq, In, NIn don't allow float and double values. Only integers and
    // strings.
    // - LT, GT, LTE, GTE allow only numbers, not alphabets.
    // - For In and NIn conditions, if the key doesn't exist in the metadata, it is
    // not matched.

    protected static Stream<Arguments> should_filter_by_metadata_not_qdrant() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata_not().filter(arguments -> {
            Filter filter = (Filter) arguments.get()[0];
            if (filter instanceof IsNotIn) {
                try {
                    IsNotIn notIn = (IsNotIn) filter;
                    Method method = notIn.getClass().getMethod("key");
                    String key = (String) method.invoke(filter);

                    List<Metadata> matchingMetadatas =
                            (List<Metadata>) arguments.get()[1];
                    // For NIn conditions, if the key doesn't exist in the metadata it is not
                    // matched.
                    Boolean matching = matchingMetadatas.stream().allMatch(metadata -> metadata.containsKey(key));
                    if (!matching) {
                        return false;
                    }

                    Object firstValue =
                            notIn.comparisonValues().stream().findFirst().get();
                    return firstValue instanceof String
                            || firstValue instanceof UUID
                            || firstValue instanceof Integer
                            || firstValue instanceof Long;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (filter instanceof final IsNotEqualTo notEqualTo) {
                return notEqualTo.comparisonValue() instanceof String
                        || notEqualTo.comparisonValue() instanceof UUID
                        || notEqualTo.comparisonValue() instanceof Integer
                        || notEqualTo.comparisonValue() instanceof Long;
            } else {
                return true;
            }
        });
    }

    @Override
    @ParameterizedTest
    @MethodSource("should_filter_by_metadata_qdrant")
    protected void should_filter_by_metadata(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    protected static Stream<Arguments> should_filter_by_metadata_qdrant() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata().filter(arguments -> {
            Filter filter = (Filter) arguments.get()[0];
            if (filter instanceof final IsLessThan lessThan) {
                return lessThan.comparisonValue() instanceof Integer || lessThan.comparisonValue() instanceof Long;
            } else if (filter instanceof final IsLessThanOrEqualTo lessThanOrEqualTo) {
                return lessThanOrEqualTo.comparisonValue() instanceof Integer
                        || lessThanOrEqualTo.comparisonValue() instanceof Long;
            } else if (filter instanceof final IsGreaterThan greaterThan) {
                return greaterThan.comparisonValue() instanceof Integer
                        || greaterThan.comparisonValue() instanceof Long;
            } else if (filter instanceof final IsGreaterThanOrEqualTo greaterThanOrEqualTo) {
                return greaterThanOrEqualTo.comparisonValue() instanceof Integer
                        || greaterThanOrEqualTo.comparisonValue() instanceof Long;
            } else if (filter instanceof final IsEqualTo equalTo) {
                return equalTo.comparisonValue() instanceof String
                        || equalTo.comparisonValue() instanceof UUID
                        || equalTo.comparisonValue() instanceof Integer
                        || equalTo.comparisonValue() instanceof Long;
            } else if (filter instanceof final IsIn in) {
                Object firstValue = in.comparisonValues().stream().findFirst().get();
                return firstValue instanceof String
                        || firstValue instanceof UUID
                        || firstValue instanceof Integer
                        || firstValue instanceof Long;
            } else {
                return true;
            }
        });
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }
}
