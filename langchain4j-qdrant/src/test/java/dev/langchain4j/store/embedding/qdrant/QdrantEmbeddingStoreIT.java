package dev.langchain4j.store.embedding.qdrant;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.filter.Filter;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static dev.langchain4j.store.embedding.filter.Filter.and;
import static dev.langchain4j.store.embedding.filter.Filter.not;
import static dev.langchain4j.store.embedding.filter.Filter.or;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import static dev.langchain4j.internal.Utils.randomUUID;

@Testcontainers
class QdrantEmbeddingStoreIT extends EmbeddingStoreIT {
    protected static final UUID TEST_UUID = UUID.randomUUID();
    static final UUID TEST_UUID2 = UUID.randomUUID();
    private static String collectionName = "langchain4j-" + randomUUID();
    private static int dimension = 384;
    private static Distance distance = Distance.Cosine;
    private static QdrantEmbeddingStore embeddingStore;

    @Container
    private static final QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:v1.11.1");

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

    @ParameterizedTest
    @MethodSource
    void should_filter_by_metadata_not(Filter metadataFilter,
        List < Metadata > matchingMetadatas,
        List < Metadata > notMatchingMetadatas) {
        // given
        List < Embedding > embeddings = new ArrayList < > ();
        List < TextSegment > segments = new ArrayList < > ();

        for (Metadata matchingMetadata: matchingMetadatas) {
            TextSegment matchingSegment = TextSegment.from("matching", matchingMetadata);
            Embedding matchingEmbedding = embeddingModel().embed(matchingSegment).content();
            embeddings.add(matchingEmbedding);
            segments.add(matchingSegment);
        }

        for (Metadata notMatchingMetadata: notMatchingMetadatas) {
            TextSegment notMatchingSegment = TextSegment.from("not matching", notMatchingMetadata);
            Embedding notMatchingEmbedding = embeddingModel().embed(notMatchingSegment).content();
            embeddings.add(notMatchingEmbedding);
            segments.add(notMatchingSegment);
        }

        embeddingStore().addAll(embeddings, segments);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(embeddings.size()));

        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(embeddingModel().embed("matching").content())
            .filter(metadataFilter)
            .maxResults(100)
            .build();

        // when
        List < EmbeddingMatch < TextSegment >> matches = embeddingStore().search(embeddingSearchRequest).matches();

        // then
        assertThat(matches).hasSize(matchingMetadatas.size());
        matches.forEach(match -> assertThat(match.embedded().text()).isEqualTo("matching"));
        matches.forEach(match -> assertThat(match.score()).isCloseTo(1, withPercentage(0.01)));
    }

    protected static Stream < Arguments > should_filter_by_metadata_not() {
        return Stream. < Arguments > builder()

            // === Not ===
            .add(Arguments.of(
                not(
                    metadataKey("name").isEqualTo("Klaus")),
                asList(
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("age", 42),
                    new Metadata()),
                asList(
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("age", 42))))

            // // === NotEqual ===

            .add(Arguments.of(
                metadataKey("key").isNotEqualTo("a"),
                asList(
                    new Metadata().put("key", "A"), //
                    new Metadata().put("key", "b"), //
                    new Metadata().put("key", "aa"), //
                    new Metadata().put("key", "a a"), //
                    new Metadata().put("key2", "a"),
                    new Metadata()),
                asList(
                    new Metadata().put("key", "a"),
                    new Metadata().put("key", "a").put("key2", "b"))))
            .add(Arguments.of(
                metadataKey("key").isNotEqualTo(TEST_UUID),
                asList(
                    new Metadata().put("key", UUID.randomUUID()),
                    new Metadata().put("key2", TEST_UUID),
                    new Metadata()),
                asList(
                    new Metadata().put("key", TEST_UUID),
                    new Metadata().put("key", TEST_UUID).put("key2",
                        UUID.randomUUID()))))
            .add(Arguments.of(
                metadataKey("key").isNotEqualTo(1),
                asList(
                    new Metadata().put("key", -1),
                    new Metadata().put("key", 0),
                    new Metadata().put("key", 2),
                    new Metadata().put("key", 10),
                    new Metadata().put("key2", 1),
                    new Metadata()),
                asList(
                    new Metadata().put("key", 1),
                    new Metadata().put("key", 1).put("key2", 2))))
            .add(Arguments.of(
                metadataKey("key").isNotEqualTo(1L),
                asList(
                    new Metadata().put("key", -1L),
                    new Metadata().put("key", 0L),
                    new Metadata().put("key", 2L),
                    new Metadata().put("key", 10L),
                    new Metadata().put("key2", 1L),
                    new Metadata()),
                asList(
                    new Metadata().put("key", 1L),
                    new Metadata().put("key", 1L).put("key2", 2L))))

            // // === NotIn ===

            // NotIn: string
            .add(Arguments.of(
                metadataKey("name").isNotIn("Klaus"),
                asList(
                    new Metadata().put("name", "Klaus Heisler"),
                    new Metadata().put("name", "Alice")),
                asList(
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("age", 42))))
            .add(Arguments.of(
                metadataKey("name").isNotIn("Klaus", "Alice"),
                asList(
                    new Metadata().put("name", "Klaus Heisler"),
                    new Metadata().put("name", "Zoe")),
                asList(
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("name", "Alice").put("age", 42))))

            // // NotIn: UUID
            .add(Arguments.of(
                metadataKey("name").isNotIn(TEST_UUID),
                asList(
                    new Metadata().put("name", UUID.randomUUID())),
                asList(
                    new Metadata().put("name", TEST_UUID),
                    new Metadata().put("name", TEST_UUID).put("age", 42))))
            .add(Arguments.of(
                metadataKey("name").isNotIn(singletonList(TEST_UUID)),
                asList(
                    new Metadata().put("name", UUID.randomUUID()),
                    new Metadata().put("name", TEST_UUID2)),
                asList(
                    new Metadata().put("name", TEST_UUID),
                    new Metadata().put("name", TEST_UUID).put("age", 42))))
            .add(Arguments.of(
                metadataKey("name").isNotIn(TEST_UUID, TEST_UUID2),
                asList(
                    new Metadata().put("name", UUID.randomUUID())),
                asList(
                    new Metadata().put("name", TEST_UUID),
                    new Metadata().put("name", TEST_UUID).put("age", 42),
                    new Metadata().put("name", TEST_UUID2),
                    new Metadata().put("name", TEST_UUID2).put("age", 42))))
            .add(Arguments.of(
                metadataKey("name").isNotIn(asList(TEST_UUID, TEST_UUID2)),
                asList(
                    new Metadata().put("name", UUID.randomUUID())),
                asList(
                    new Metadata().put("name", TEST_UUID),
                    new Metadata().put("name", TEST_UUID).put("age", 42),
                    new Metadata().put("name", TEST_UUID2),
                    new Metadata().put("name", TEST_UUID2).put("age", 42))))

            // // NotIn: int
            .add(Arguments.of(
                metadataKey("age").isNotIn(42),
                asList(
                    new Metadata().put("age", 666)),
                asList(
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 42).put("name", "Klaus"))))
            .add(Arguments.of(
                metadataKey("age").isNotIn(singletonList(42)),
                asList(
                    new Metadata().put("age", 666)),
                asList(
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 42).put("name", "Klaus"))))
            .add(Arguments.of(
                metadataKey("age").isNotIn(42, 18),
                asList(
                    new Metadata().put("age", 666)),
                asList(
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 18),
                    new Metadata().put("age", 42).put("name", "Klaus"),
                    new Metadata().put("age", 18).put("name", "Klaus"))))
            .add(Arguments.of(
                metadataKey("age").isNotIn(asList(42, 18)),
                asList(
                    new Metadata().put("age", 666)),
                asList(
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 18),
                    new Metadata().put("age", 42).put("name", "Klaus"),
                    new Metadata().put("age", 18).put("name", "Klaus"))))

            // NotIn: long
            .add(Arguments.of(
                metadataKey("age").isNotIn(42L),
                asList(
                    new Metadata().put("age", 666L)),
                asList(
                    new Metadata().put("age", 42L),
                    new Metadata().put("age", 42L).put("name", "Klaus"))))
            .add(Arguments.of(
                metadataKey("age").isNotIn(singletonList(42L)),
                asList(
                    new Metadata().put("age", 666L)),
                asList(
                    new Metadata().put("age", 42L),
                    new Metadata().put("age", 42L).put("name", "Klaus"))))
            .add(Arguments.of(
                metadataKey("age").isNotIn(42L, 18L),
                asList(
                    new Metadata().put("age", 666L)),
                asList(
                    new Metadata().put("age", 42L),
                    new Metadata().put("age", 18L),
                    new Metadata().put("age", 42L).put("name", "Klaus"),
                    new Metadata().put("age", 18L).put("name", "Klaus"))))
            .add(Arguments.of(
                metadataKey("age").isNotIn(asList(42L, 18L)),
                asList(
                    new Metadata().put("age", 666L)),
                asList(
                    new Metadata().put("age", 42L),
                    new Metadata().put("age", 18L),
                    new Metadata().put("age", 42L).put("name", "Klaus"),
                    new Metadata().put("age", 18L).put("name", "Klaus"))))

            .build();
    }

    @ParameterizedTest
    @MethodSource
    protected void should_filter_by_metadata(Filter metadataFilter,
        List < Metadata > matchingMetadatas,
        List < Metadata > notMatchingMetadatas) {
        // given
        List < Embedding > embeddings = new ArrayList < > ();
        List < TextSegment > segments = new ArrayList < > ();

        for (Metadata matchingMetadata: matchingMetadatas) {
            TextSegment matchingSegment = TextSegment.from("matching", matchingMetadata);
            Embedding matchingEmbedding = embeddingModel().embed(matchingSegment).content();
            embeddings.add(matchingEmbedding);
            segments.add(matchingSegment);
        }

        for (Metadata notMatchingMetadata: notMatchingMetadatas) {
            TextSegment notMatchingSegment = TextSegment.from("not matching", notMatchingMetadata);
            Embedding notMatchingEmbedding = embeddingModel().embed(notMatchingSegment).content();
            embeddings.add(notMatchingEmbedding);
            segments.add(notMatchingSegment);
        }

        embeddingStore().addAll(embeddings, segments);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(embeddings.size()));

        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(embeddingModel().embed("matching").content())
            .filter(metadataFilter)
            .maxResults(100)
            .build();

        // when
        List < EmbeddingMatch < TextSegment >> matches = embeddingStore().search(embeddingSearchRequest).matches();

        // then
        assertThat(matches).hasSize(matchingMetadatas.size());
        matches.forEach(match -> assertThat(match.embedded().text()).isEqualTo("matching"));
        matches.forEach(match -> assertThat(match.score()).isCloseTo(1, withPercentage(0.01)));
    }

    protected static Stream < Arguments > should_filter_by_metadata() {
        return Stream. < Arguments > builder()

            // === Equal ===

            .add(Arguments.of(
                metadataKey("key").isEqualTo("a"),
                asList(
                    new Metadata().put("key", "a"),
                    new Metadata().put("key", "a").put("key2", "b")),
                asList(
                    new Metadata().put("key", "A"),
                    new Metadata().put("key", "b"),
                    new Metadata().put("key", "aa"),
                    new Metadata().put("key", "a a"),
                    new Metadata().put("key2", "a"),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isEqualTo(TEST_UUID),
                asList(
                    new Metadata().put("key", TEST_UUID),
                    new Metadata().put("key", TEST_UUID).put("key2", "b")),
                asList(
                    new Metadata().put("key", UUID.randomUUID()),
                    new Metadata().put("key2", TEST_UUID),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isEqualTo(1),
                asList(
                    new Metadata().put("key", 1),
                    new Metadata().put("key", 1).put("key2", 0)),
                asList(
                    new Metadata().put("key", -1),
                    new Metadata().put("key", 0),
                    new Metadata().put("key2", 1),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isEqualTo(1L),
                asList(
                    new Metadata().put("key", 1L),
                    new Metadata().put("key", 1L).put("key2", 0L)),
                asList(
                    new Metadata().put("key", -1L),
                    new Metadata().put("key", 0L),
                    new Metadata().put("key2", 1L),
                    new Metadata())))

            // // === GreaterThan ==

            .add(Arguments.of(
                metadataKey("key").isGreaterThan(1),
                asList(
                    new Metadata().put("key", 2),
                    new Metadata().put("key", 2).put("key2", 0)),
                asList(
                    new Metadata().put("key", -2),
                    new Metadata().put("key", 0),
                    new Metadata().put("key", 1),
                    new Metadata().put("key2", 2),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isGreaterThan(1L),
                asList(
                    new Metadata().put("key", 2L),
                    new Metadata().put("key", 2L).put("key2", 0L)),
                asList(
                    new Metadata().put("key", -2L),
                    new Metadata().put("key", 0L),
                    new Metadata().put("key", 1L),
                    new Metadata().put("key2", 2L),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isGreaterThan(1.1f),
                asList(
                    new Metadata().put("key", 1.2f),
                    new Metadata().put("key", 1.2f).put("key2", 1.0f)),
                asList(
                    new Metadata().put("key", -1.2f),
                    new Metadata().put("key", 0.0f),
                    new Metadata().put("key", 0.79f),
                    new Metadata().put("key2", 1.2f),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isGreaterThan(1.1d),
                asList(
                    new Metadata().put("key", 1.2d),
                    new Metadata().put("key", 1.2d).put("key2", 1.0d)),
                asList(
                    new Metadata().put("key", -1.2d),
                    new Metadata().put("key", 0.0d),
                    new Metadata().put("key", 1.1d),
                    new Metadata().put("key2", 1.2d),
                    new Metadata())))

            // // === GreaterThanOrEqual ==

            .add(Arguments.of(
                metadataKey("key").isGreaterThanOrEqualTo(1),
                asList(
                    new Metadata().put("key", 1),
                    new Metadata().put("key", 2),
                    new Metadata().put("key", 2).put("key2", 0)),
                asList(
                    new Metadata().put("key", -2),
                    new Metadata().put("key", -1),
                    new Metadata().put("key", 0),
                    new Metadata().put("key2", 1),
                    new Metadata().put("key2", 2),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isGreaterThanOrEqualTo(1L),
                asList(
                    new Metadata().put("key", 1L),
                    new Metadata().put("key", 2L),
                    new Metadata().put("key", 2L).put("key2", 0L)),
                asList(
                    new Metadata().put("key", -2L),
                    new Metadata().put("key", -1L),
                    new Metadata().put("key", 0L),
                    new Metadata().put("key2", 1L),
                    new Metadata().put("key2", 2L),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isGreaterThanOrEqualTo(1.1f),
                asList(
                    new Metadata().put("key", 1.1f),
                    new Metadata().put("key", 1.2f),
                    new Metadata().put("key", 1.2f).put("key2", 1.0f)),
                asList(
                    new Metadata().put("key", -1.2f),
                    new Metadata().put("key", -1.1f),
                    new Metadata().put("key", 0.0f),
                    new Metadata().put("key2", 1.1f),
                    new Metadata().put("key2", 1.2f),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isGreaterThanOrEqualTo(1.1d),
                asList(
                    new Metadata().put("key", 1.1d),
                    new Metadata().put("key", 1.2d),
                    new Metadata().put("key", 1.2d).put("key2", 1.0d)),
                asList(
                    new Metadata().put("key", -1.2d),
                    new Metadata().put("key", -1.1d),
                    new Metadata().put("key", 0.0d),
                    new Metadata().put("key2", 1.1d),
                    new Metadata().put("key2", 1.2d),
                    new Metadata())))

            // === LessThan ==

            .add(Arguments.of(
                metadataKey("key").isLessThan(1),
                asList(
                    new Metadata().put("key", -2),
                    new Metadata().put("key", 0),
                    new Metadata().put("key", 0).put("key2", 2)),
                asList(
                    new Metadata().put("key", 1),
                    new Metadata().put("key", 2),
                    new Metadata().put("key2", 0),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isLessThan(1L),
                asList(
                    new Metadata().put("key", -2L),
                    new Metadata().put("key", 0L),
                    new Metadata().put("key", 0L).put("key2", 2L)),
                asList(
                    new Metadata().put("key", 1L),
                    new Metadata().put("key", 2L),
                    new Metadata().put("key2", 0L),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isLessThan(1.1f),
                asList(
                    new Metadata().put("key", -1.2f),
                    new Metadata().put("key", 1.0f),
                    new Metadata().put("key", 1.0f).put("key2", 1.2f)),
                asList(
                    new Metadata().put("key", 1.1f),
                    new Metadata().put("key", 1.2f),
                    new Metadata().put("key2", 1.0f),
                    new Metadata())))
            .add(Arguments.of(
                metadataKey("key").isLessThan(1.1d),
                asList(
                    new Metadata().put("key", -1.2d),
                    new Metadata().put("key", 1.0d),
                    new Metadata().put("key", 1.0d).put("key2", 1.2d)),
                asList(
                    new Metadata().put("key", 1.1d),
                    new Metadata().put("key", 1.2d),
                    new Metadata().put("key2", 1.0d),
                    new Metadata())))

            // === LessThanOrEqual ==

            .add(Arguments.of(
                metadataKey("key").isLessThanOrEqualTo(1),
                asList(
                    new Metadata().put("key", -2),
                    new Metadata().put("key", 0),
                    new Metadata().put("key", 1),
                    new Metadata().put("key", 1).put("key2", 2)
                ),
                asList(
                    new Metadata().put("key", 2),
                    new Metadata().put("key2", 0),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("key").isLessThanOrEqualTo(1L),
                asList(
                    new Metadata().put("key", -2L),
                    new Metadata().put("key", 0L),
                    new Metadata().put("key", 1L),
                    new Metadata().put("key", 1L).put("key2", 2L)
                ),
                asList(
                    new Metadata().put("key", 2L),
                    new Metadata().put("key2", 0L),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("key").isLessThanOrEqualTo(1.1f),
                asList(
                    new Metadata().put("key", -1.2f),
                    new Metadata().put("key", 1.0f),
                    new Metadata().put("key", 1.09f),
                    new Metadata().put("key", 1.08f).put("key2", 1.2f)
                ),
                asList(
                    new Metadata().put("key", 1.2f),
                    new Metadata().put("key2", 1.0f),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("key").isLessThanOrEqualTo(1.1d),
                asList(
                    new Metadata().put("key", -1.2d),
                    new Metadata().put("key", 1.0d),
                    new Metadata().put("key", 1.1d),
                    new Metadata().put("key", 1.1d).put("key2", 1.2d)
                ),
                asList(
                    new Metadata().put("key", 1.2d),
                    new Metadata().put("key2", 1.0d),
                    new Metadata()
                )
            ))

            // === In ===

            // In: string
            .add(Arguments.of(
                metadataKey("name").isIn("Klaus"),
                asList(
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("age", 42)
                ),
                asList(
                    new Metadata().put("name", "Klaus Heisler"),
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("name2", "Klaus"),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("name").isIn("Klaus", "Alice"),
                asList(
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("name", "Alice").put("age", 42)
                ),
                asList(
                    new Metadata().put("name", "Klaus Heisler"),
                    new Metadata().put("name", "Zoe"),
                    new Metadata().put("name2", "Klaus"),
                    new Metadata()
                )
            ))

            // In: UUID
            .add(Arguments.of(
                metadataKey("name").isIn(TEST_UUID),
                asList(
                    new Metadata().put("name", TEST_UUID),
                    new Metadata().put("name", TEST_UUID).put("age", 42)
                ),
                asList(
                    new Metadata().put("name", UUID.randomUUID()),
                    new Metadata().put("name2", TEST_UUID),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("name").isIn(singletonList(TEST_UUID)),
                asList(
                    new Metadata().put("name", TEST_UUID),
                    new Metadata().put("name", TEST_UUID).put("age", 42)
                ),
                asList(
                    new Metadata().put("name", UUID.randomUUID()),
                    new Metadata().put("name2", TEST_UUID),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("name").isIn(TEST_UUID, TEST_UUID2),
                asList(
                    new Metadata().put("name", TEST_UUID),
                    new Metadata().put("name", TEST_UUID).put("age", 42),
                    new Metadata().put("name", TEST_UUID2),
                    new Metadata().put("name", TEST_UUID2).put("age", 42)
                ),
                asList(
                    new Metadata().put("name", UUID.randomUUID()),
                    new Metadata().put("name2", TEST_UUID),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("name").isIn(asList(TEST_UUID, TEST_UUID2)),
                asList(
                    new Metadata().put("name", TEST_UUID),
                    new Metadata().put("name", TEST_UUID).put("age", 42),
                    new Metadata().put("name", TEST_UUID2),
                    new Metadata().put("name", TEST_UUID2).put("age", 42)
                ),
                asList(
                    new Metadata().put("name", UUID.randomUUID()),
                    new Metadata().put("name2", TEST_UUID),
                    new Metadata()
                )
            ))

            // In: integer
            .add(Arguments.of(
                metadataKey("age").isIn(42),
                asList(
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 42).put("name", "Klaus")
                ),
                asList(
                    new Metadata().put("age", 666),
                    new Metadata().put("age2", 42),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("age").isIn(singletonList(42)),
                asList(
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 42).put("name", "Klaus")
                ),
                asList(
                    new Metadata().put("age", 666),
                    new Metadata().put("age2", 42),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("age").isIn(42, 18),
                asList(
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 18),
                    new Metadata().put("age", 42).put("name", "Klaus"),
                    new Metadata().put("age", 18).put("name", "Klaus")
                ),
                asList(
                    new Metadata().put("age", 666),
                    new Metadata().put("age2", 42),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("age").isIn(asList(42, 18)),
                asList(
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 18),
                    new Metadata().put("age", 42).put("name", "Klaus"),
                    new Metadata().put("age", 18).put("name", "Klaus")
                ),
                asList(
                    new Metadata().put("age", 666),
                    new Metadata().put("age2", 42),
                    new Metadata()
                )
            ))

            // In: long
            .add(Arguments.of(
                metadataKey("age").isIn(42L),
                asList(
                    new Metadata().put("age", 42L),
                    new Metadata().put("age", 42L).put("name", "Klaus")
                ),
                asList(
                    new Metadata().put("age", 666L),
                    new Metadata().put("age2", 42L),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("age").isIn(singletonList(42L)),
                asList(
                    new Metadata().put("age", 42L),
                    new Metadata().put("age", 42L).put("name", "Klaus")
                ),
                asList(
                    new Metadata().put("age", 666L),
                    new Metadata().put("age2", 42L),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("age").isIn(42L, 18L),
                asList(
                    new Metadata().put("age", 42L),
                    new Metadata().put("age", 18L),
                    new Metadata().put("age", 42L).put("name", "Klaus"),
                    new Metadata().put("age", 18L).put("name", "Klaus")
                ),
                asList(
                    new Metadata().put("age", 666L),
                    new Metadata().put("age2", 42L),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                metadataKey("age").isIn(asList(42L, 18L)),
                asList(
                    new Metadata().put("age", 42L),
                    new Metadata().put("age", 18L),
                    new Metadata().put("age", 42L).put("name", "Klaus"),
                    new Metadata().put("age", 18L).put("name", "Klaus")
                ),
                asList(
                    new Metadata().put("age", 666L),
                    new Metadata().put("age2", 42L),
                    new Metadata()
                )
            ))


            // === Or ===

            // Or: one key
            .add(Arguments.of(
                or(
                    metadataKey("name").isEqualTo("Klaus"),
                    metadataKey("name").isEqualTo("Alice")
                ),
                asList(
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("name", "Alice").put("age", 42)
                ),
                asList(
                    new Metadata().put("name", "Zoe"),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                or(
                    metadataKey("name").isEqualTo("Alice"),
                    metadataKey("name").isEqualTo("Klaus")
                ),
                asList(
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("name", "Alice").put("age", 42),
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("age", 42)
                ),
                asList(
                    new Metadata().put("name", "Zoe"),
                    new Metadata()
                )
            ))

            // Or: multiple keys
            .add(Arguments.of(
                or(
                    metadataKey("name").isEqualTo("Klaus"),
                    metadataKey("age").isEqualTo(42)
                ),
                asList(
                    // only Or.left is present and true
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("city", "Munich"),

                    // Or.left is true, Or.right is false
                    new Metadata().put("name", "Klaus").put("age", 666),

                    // only Or.right is present and true
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 42).put("city", "Munich"),

                    // Or.right is true, Or.left is false
                    new Metadata().put("age", 42).put("name", "Alice"),

                    // Or.left and Or.right are both true
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich")
                ),
                asList(
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("age", 666),
                    new Metadata().put("name", "Alice").put("age", 666),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                or(
                    metadataKey("age").isEqualTo(42),
                    metadataKey("name").isEqualTo("Klaus")
                ),
                asList(
                    // only Or.left is present and true
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 42).put("city", "Munich"),

                    // Or.left is true, Or.right is false
                    new Metadata().put("age", 42).put("name", "Alice"),

                    // only Or.right is present and true
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("city", "Munich"),

                    // Or.right is true, Or.left is false
                    new Metadata().put("name", "Klaus").put("age", 666),

                    // Or.left and Or.right are both true
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich")
                ),
                asList(
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("age", 666),
                    new Metadata().put("name", "Alice").put("age", 666),
                    new Metadata()
                )
            ))

            // Or: x2
            .add(Arguments.of(
                or(
                    metadataKey("name").isEqualTo("Klaus"),
                    or(
                        metadataKey("age").isEqualTo(42),
                        metadataKey("city").isEqualTo("Munich")
                    )
                ),
                asList(
                    // only Or.left is present and true
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("country", "Germany"),

                    // Or.left is true, Or.right is false
                    new Metadata().put("name", "Klaus").put("age", 666),
                    new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                    new Metadata().put("name", "Klaus").put("age", 666).put("city", "Frankfurt"),

                    // only Or.right is present and true
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 42).put("country", "Germany"),
                    new Metadata().put("city", "Munich"),
                    new Metadata().put("city", "Munich").put("country", "Germany"),
                    new Metadata().put("age", 42).put("city", "Munich"),
                    new Metadata().put("age", 42).put("city", "Munich").put("country",
                        "Germany"),

                    // Or.right is true, Or.left is false
                    new Metadata().put("age", 42).put("name", "Alice"),
                    new Metadata().put("city", "Munich").put("name", "Alice"),
                    new Metadata().put("age", 42).put("city", "Munich").put("name", "Alice"),

                    // Or.left and Or.right are both true
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Klaus").put("age", 42).put("country", "Germany"),
                    new Metadata().put("name", "Klaus").put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("city", "Munich").put("country",
                        "Germany"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city",
                        "Munich").put("country", "Germany")
                ),
                asList(
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("age", 666),
                    new Metadata().put("city", "Frankfurt"),
                    new Metadata().put("name", "Alice").put("age", 666),
                    new Metadata().put("name", "Alice").put("city", "Frankfurt"),
                    new Metadata().put("name", "Alice").put("age", 666).put("city", "Frankfurt"),
                    new Metadata()
                )
            ))
            .add(Arguments.of(
                or(
                    or(
                        metadataKey("name").isEqualTo("Klaus"),
                        metadataKey("age").isEqualTo(42)
                    ),
                    metadataKey("city").isEqualTo("Munich")
                ),
                asList(
                    // only Or.left is present and true
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("country", "Germany"),
                    new Metadata().put("age", 42),
                    new Metadata().put("age", 42).put("country", "Germany"),
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Klaus").put("age", 42).put("country", "Germany"),

                    // Or.left is true, Or.right is false
                    new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                    new Metadata().put("age", 42).put("city", "Frankfurt"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Frankfurt"),

                    // only Or.right is present and true
                    new Metadata().put("city", "Munich"),
                    new Metadata().put("city", "Munich").put("country", "Germany"),

                    // Or.right is true, Or.left is false
                    new Metadata().put("city", "Munich").put("name", "Alice"),
                    new Metadata().put("city", "Munich").put("age", 666),

                    // Or.left and Or.right are both true
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Klaus").put("age", 42).put("country", "Germany"),
                    new Metadata().put("name", "Klaus").put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("city", "Munich").put("country",
                        "Germany"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city",
                        "Munich").put("country", "Germany")
                ),
                asList(
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("age", 666),
                    new Metadata().put("city", "Frankfurt"),
                    new Metadata().put("name", "Alice").put("age", 666),
                    new Metadata().put("name", "Alice").put("city", "Frankfurt"),
                    new Metadata().put("name", "Alice").put("age", 666).put("city", "Frankfurt"),
                    new Metadata()
                )
            ))

            // === AND ===

            .add(Arguments.of(
                and(
                    metadataKey("name").isEqualTo("Klaus"),
                    metadataKey("age").isEqualTo(42)
                ),
                asList(
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich")
                ),
                asList(
                    // only And.left is present and true
                    new Metadata().put("name", "Klaus"),

                    // And.left is true, And.right is false
                    new Metadata().put("name", "Klaus").put("age", 666),

                    // only And.right is present and true
                    new Metadata().put("age", 42),

                    // And.right is true, And.left is false
                    new Metadata().put("age", 42).put("name", "Alice"),

                    // And.left, And.right are both false
                    new Metadata().put("age", 666).put("name", "Alice"),

                    new Metadata()
                )
            ))
            .add(Arguments.of(
                and(
                    metadataKey("age").isEqualTo(42),
                    metadataKey("name").isEqualTo("Klaus")
                ),
                asList(
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich")
                ),
                asList(
                    // only And.left is present and true
                    new Metadata().put("age", 42),

                    // And.left is true, And.right is false
                    new Metadata().put("age", 42).put("name", "Alice"),

                    // only And.right is present and true
                    new Metadata().put("name", "Klaus"),

                    // And.right is true, And.left is false
                    new Metadata().put("name", "Klaus").put("age", 666),

                    // And.left, And.right are both false
                    new Metadata().put("age", 666).put("name", "Alice"),

                    new Metadata()
                )
            ))

            // And: x2
            .add(Arguments.of(
                and(
                    metadataKey("name").isEqualTo("Klaus"),
                    and(
                        metadataKey("age").isEqualTo(42),
                        metadataKey("city").isEqualTo("Munich")
                    )
                ),
                asList(
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city",
                        "Munich").put("country", "Germany")
                ),
                asList(
                    // only And.left is present and true
                    new Metadata().put("name", "Klaus"),

                    // And.left is true, And.right is false
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Klaus").put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("age", 666).put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Frankfurt"),

                    // only And.right is present and true
                    new Metadata().put("age", 42).put("city", "Munich"),

                    // And.right is true, And.left is false
                    new Metadata().put("age", 42).put("city", "Munich").put("name", "Alice"),

                    new Metadata()
                )
            ))
            .add(Arguments.of(
                and(
                    and(
                        metadataKey("name").isEqualTo("Klaus"),
                        metadataKey("age").isEqualTo(42)
                    ),
                    metadataKey("city").isEqualTo("Munich")
                ),
                asList(
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city",
                        "Munich").put("country", "Germany")
                ),
                asList(
                    // only And.left is present and true
                    new Metadata().put("name", "Klaus").put("age", 42),

                    // And.left is true, And.right is false
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Frankfurt"),

                    // only And.right is present and true
                    new Metadata().put("city", "Munich"),

                    // And.right is true, And.left is false
                    new Metadata().put("city", "Munich").put("name", "Klaus"),
                    new Metadata().put("city", "Munich").put("name", "Klaus").put("age", 666),
                    new Metadata().put("city", "Munich").put("age", 42),
                    new Metadata().put("city", "Munich").put("age", 42).put("name", "Alice"),

                    new Metadata()
                )
            ))

            // === AND + nested OR ===

            .add(Arguments.of(
                and(
                    metadataKey("name").isEqualTo("Klaus"),
                    or(
                        metadataKey("age").isEqualTo(42),
                        metadataKey("city").isEqualTo("Munich")
                    )
                ),
                asList(
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Klaus").put("age", 42).put("country", "Germany"),
                    new Metadata().put("name", "Klaus").put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("city", "Munich").put("country",
                        "Germany"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city",
                        "Munich").put("country", "Germany")
                ),
                asList(
                    // only And.left is present and true
                    new Metadata().put("name", "Klaus"),

                    // And.left is true, And.right is false
                    new Metadata().put("name", "Klaus").put("age", 666),
                    new Metadata().put("name", "Klaus").put("city", "Frankfurt"),

                    // only And.right is present and true
                    new Metadata().put("age", 42),
                    new Metadata().put("city", "Munich"),
                    new Metadata().put("age", 42).put("city", "Munich"),

                    // And.right is true, And.left is false
                    new Metadata().put("age", 42).put("name", "Alice"),
                    new Metadata().put("city", "Munich").put("name", "Alice"),
                    new Metadata().put("age", 42).put("city", "Munich").put("name", "Alice"),

                    new Metadata()
                )
            ))
            .add(Arguments.of(
                and(
                    or(
                        metadataKey("name").isEqualTo("Klaus"),
                        metadataKey("age").isEqualTo(42)
                    ),
                    metadataKey("city").isEqualTo("Munich")
                ),
                asList(
                    new Metadata().put("name", "Klaus").put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("city", "Munich").put("country",
                        "Germany"),
                    new Metadata().put("age", 42).put("city", "Munich"),
                    new Metadata().put("age", 42).put("city", "Munich").put("country",
                        "Germany"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city",
                        "Munich").put("country", "Germany")
                ),
                asList(
                    // only And.left is present and true
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("age", 42),
                    new Metadata().put("name", "Klaus").put("age", 42),

                    // And.left is true, And.right is false
                    new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                    new Metadata().put("age", 42).put("city", "Frankfurt"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Frankfurt"),

                    // only And.right is present and true
                    new Metadata().put("city", "Munich"),

                    // And.right is true, And.left is false
                    new Metadata().put("city", "Munich").put("name", "Alice"),
                    new Metadata().put("city", "Munich").put("age", 666),
                    new Metadata().put("city", "Munich").put("name", "Alice").put("age", 666),

                    new Metadata()
                )
            ))

            // === OR + nested AND ===
            .add(Arguments.of(
                or(
                    metadataKey("name").isEqualTo("Klaus"),
                    and(
                        metadataKey("age").isEqualTo(42),
                        metadataKey("city").isEqualTo("Munich")
                    )
                ),
                asList(
                    // only Or.left is present and true
                    new Metadata().put("name", "Klaus"),
                    new Metadata().put("name", "Klaus").put("country", "Germany"),

                    // Or.left is true, Or.right is false
                    new Metadata().put("name", "Klaus").put("age", 666),
                    new Metadata().put("name", "Klaus").put("city", "Frankfurt"),
                    new Metadata().put("name", "Klaus").put("age", 666).put("city", "Frankfurt"),

                    // only Or.right is present and true
                    new Metadata().put("age", 42).put("city", "Munich"),
                    new Metadata().put("age", 42).put("city", "Munich").put("country",
                        "Germany"),

                    // Or.right is true, Or.left is false
                    new Metadata().put("age", 42).put("city", "Munich").put("name", "Alice")
                ),
                asList(
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("age", 666),
                    new Metadata().put("city", "Frankfurt"),
                    new Metadata().put("name", "Alice").put("age", 666).put("city", "Frankfurt"),

                    new Metadata()
                )
            ))
            .add(Arguments.of(
                or(
                    and(
                        metadataKey("name").isEqualTo("Klaus"),
                        metadataKey("age").isEqualTo(42)
                    ),
                    metadataKey("city").isEqualTo("Munich")
                ),
                asList(
                    // only Or.left is present and true
                    new Metadata().put("name", "Klaus").put("age", 42),
                    new Metadata().put("name", "Klaus").put("age", 42).put("country", "Germany"),

                    // Or.left is true, Or.right is false
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Frankfurt"),

                    // only Or.right is present and true
                    new Metadata().put("city", "Munich"),
                    new Metadata().put("city", "Munich").put("country", "Germany"),

                    // Or.right is true, Or.left is true
                    new Metadata().put("name", "Klaus").put("age", 42).put("city", "Munich"),
                    new Metadata().put("name", "Klaus").put("age", 42).put("city",
                        "Munich").put("country", "Germany")
                ),
                asList(
                    new Metadata().put("name", "Alice"),
                    new Metadata().put("age", 666),
                    new Metadata().put("city", "Frankfurt"),
                    new Metadata().put("name", "Alice").put("age", 666).put("city", "Frankfurt"),
                    new Metadata()
                )
            ))

            .build();
    }
}