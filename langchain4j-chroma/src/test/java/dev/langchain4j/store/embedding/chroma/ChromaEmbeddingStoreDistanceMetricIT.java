package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.chroma.ChromaApiVersion.V1;
import static dev.langchain4j.store.embedding.chroma.ChromaApiVersion.V2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that relevance scores stay in the [0, 1] range for an already existing collection,
 * whichever distance metric that collection was created with.
 */
@Testcontainers
class ChromaEmbeddingStoreDistanceMetricIT {

    @Container
    private static final ChromaDBContainer chromaV1 = new ChromaDBContainer("chromadb/chroma:0.5.4");

    @Container
    private static final ChromaDBContainer chromaV2 = new ChromaDBContainer("chromadb/chroma:1.1.0");

    private static final Embedding QUERY = Embedding.from(List.of(1f, 0f, 0f));
    private static final Embedding NEAR = Embedding.from(List.of(2f, 0f, 0f));
    private static final Embedding FAR = Embedding.from(List.of(10f, 0f, 0f));

    static Stream<ChromaApiVersion> apiVersions() {
        return Stream.of(V1, V2);
    }

    /**
     * The container is deliberately not passed as a test argument: JUnit closes {@link AutoCloseable} arguments
     * after each invocation, which would stop the container.
     */
    private static ChromaDBContainer chroma(ChromaApiVersion apiVersion) {
        return apiVersion == V1 ? chromaV1 : chromaV2;
    }

    /**
     * "l2" is the squared euclidean distance and is unbounded, so the distance to {@link #FAR} is 81.
     * Converting it as if it were a cosine distance used to yield a score of -39.5.
     */
    @ParameterizedTest
    @MethodSource("apiVersions")
    void should_return_valid_scores_for_an_existing_l2_collection(ChromaApiVersion apiVersion) throws IOException {
        String collectionName = randomUUID();
        createCollection(apiVersion, collectionName, "l2");

        ChromaEmbeddingStore store = store(apiVersion, collectionName);
        store.addAll(List.of("near", "far"), List.of(NEAR, FAR), null);

        List<EmbeddingMatch<TextSegment>> matches = search(store);

        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly("near", "far");
        assertThat(matches)
                .extracting(EmbeddingMatch::score)
                .allSatisfy(score -> assertThat(score).isBetween(0.0, 1.0));
        assertThat(matches.get(0).score()).isCloseTo(1.0 / 2, within(1e-4)); // distance 1
        assertThat(matches.get(1).score()).isCloseTo(1.0 / 82, within(1e-4)); // distance 81
    }

    @ParameterizedTest
    @MethodSource("apiVersions")
    void should_return_valid_scores_for_an_existing_cosine_collection(ChromaApiVersion apiVersion) throws IOException {
        String collectionName = randomUUID();
        createCollection(apiVersion, collectionName, "cosine");

        ChromaEmbeddingStore store = store(apiVersion, collectionName);
        store.addAll(List.of("near", "far"), List.of(NEAR, FAR), null);

        List<EmbeddingMatch<TextSegment>> matches = search(store);

        // all embeddings point in the same direction, so the cosine distance is 0 for both
        assertThat(matches)
                .extracting(EmbeddingMatch::score)
                .allSatisfy(score -> assertThat(score).isCloseTo(1.0, within(1e-4)));
    }

    @ParameterizedTest
    @MethodSource("apiVersions")
    void should_use_cosine_distance_for_a_collection_created_by_this_store(ChromaApiVersion apiVersion) {
        String collectionName = randomUUID();

        store(apiVersion, collectionName); // creates the collection

        ChromaEmbeddingStore store = store(apiVersion, collectionName); // reopens it
        store.addAll(List.of("near", "far"), List.of(NEAR, FAR), null);

        assertThat(search(store))
                .extracting(EmbeddingMatch::score)
                .allSatisfy(score -> assertThat(score).isCloseTo(1.0, within(1e-4)));
    }

    @Test
    void should_switch_to_cosine_distance_when_remove_all_recreated_the_collection() throws IOException {
        String collectionName = randomUUID();
        createCollection(V2, collectionName, "l2");
        ChromaEmbeddingStore store = store(V2, collectionName);

        store.removeAll(); // deletes the "l2" collection and recreates it with cosine distance
        store.addAll(List.of("near", "far"), List.of(NEAR, FAR), null);

        assertThat(search(store))
                .extracting(EmbeddingMatch::score)
                .allSatisfy(score -> assertThat(score).isCloseTo(1.0, within(1e-4)));
    }

    private static List<EmbeddingMatch<TextSegment>> search(ChromaEmbeddingStore store) {
        return store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(QUERY)
                        .maxResults(10)
                        .build())
                .matches();
    }

    private static ChromaEmbeddingStore store(ChromaApiVersion apiVersion, String collectionName) {
        return ChromaEmbeddingStore.builder()
                .apiVersion(apiVersion)
                .baseUrl(chroma(apiVersion).getEndpoint())
                .collectionName(collectionName)
                .build();
    }

    private static void createCollection(ChromaApiVersion apiVersion, String name, String space) throws IOException {
        if (apiVersion == V2) {
            // the tenant and the database the store defaults to have to exist before a collection can be created
            store(V2, randomUUID());
        }

        Collection created = new ChromaHttpClient(
                        chroma(apiVersion).getEndpoint(), Duration.ofSeconds(10), false, false)
                .post(
                        collectionsPath(apiVersion),
                        Map.of("name", name, "metadata", Map.of("hnsw:space", space)),
                        Collection.class);

        assertThat(created.distanceFunction()).isEqualTo(space);
    }

    private static String collectionsPath(ChromaApiVersion apiVersion) {
        return apiVersion == V1 ? "api/v1/collections" : "api/v2/tenants/default/databases/default/collections";
    }
}
