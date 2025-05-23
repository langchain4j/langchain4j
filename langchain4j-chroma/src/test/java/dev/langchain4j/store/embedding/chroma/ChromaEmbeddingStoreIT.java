package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.logical.Not;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ChromaEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @Container
    private static final ChromaDBContainer chroma = new ChromaDBContainer("chromadb/chroma:1.0.0")
            .withExposedPorts(8000)
            // disable the built-in Docker HEALTHCHECK
            .withCreateContainerCmdModifier(
                    cmd -> cmd.withHealthcheck(new com.github.dockerjava.api.model.HealthCheck()))
            // V2 API check endpoint
            .waitingFor(new org.testcontainers.containers.wait.strategy.HttpWaitStrategy()
                    .forPath("/api/v2/version")
                    .forStatusCode(200)
                    .withStartupTimeout(java.time.Duration.ofSeconds(60)));

    ;

    EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore.builder()
            .baseUrl(chroma.getEndpoint())
            .collectionName(randomUUID())
            .logRequests(true)
            .logResponses(true)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    @ParameterizedTest
    @MethodSource("should_filter_by_metadata_chroma")
    protected void should_filter_by_metadata(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    // in chroma compare filter only works with numbers
    protected static Stream<Arguments> should_filter_by_metadata_chroma() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata().filter(arguments -> {
            Filter filter = (Filter) arguments.get()[0];
            if (filter instanceof IsLessThan) {
                return ((IsLessThan) filter).comparisonValue() instanceof Number;
            } else if (filter instanceof IsLessThanOrEqualTo) {
                return ((IsLessThanOrEqualTo) filter).comparisonValue() instanceof Number;
            } else if (filter instanceof IsGreaterThan) {
                return ((IsGreaterThan) filter).comparisonValue() instanceof Number;
            } else if (filter instanceof IsGreaterThanOrEqualTo) {
                return ((IsGreaterThanOrEqualTo) filter).comparisonValue() instanceof Number;
            } else {
                return true;
            }
        });
    }

    @Override
    @ParameterizedTest
    @MethodSource("should_filter_by_metadata_not_chroma")
    protected void should_filter_by_metadata_not(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata_not(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    // Chroma filters by *not* as following:
    // If you filter by "key" not equals "a", then in fact all items with "key" != "a" value are returned, but no items
    // without "key" metadata!
    // Therefore, all default *not* tests coming from parent class have to be rewritten here.
    protected static Stream<Arguments> should_filter_by_metadata_not_chroma() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata_not().map(args -> {
            Object[] arguments = args.get();
            Filter filter = (Filter) arguments[0];

            String key = getMetadataKey(filter);

            List<Metadata> matchingMetadatas = (List<Metadata>) arguments[1];

            List<Metadata> notMatchingMetadatas = (List<Metadata>) arguments[2];

            return Arguments.of(filter, matchingMetadatas, notMatchingMetadatas);
        });
    }

    private static String getMetadataKey(Filter filter) {
        try {
            if (filter instanceof Not) {
                filter = ((Not) filter).expression();
            }
            Method method = filter.getClass().getMethod("key");
            return (String) method.invoke(filter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // V2 API does not allow null TextSegment (text & metadata)
    @Override
    @Test
    protected void should_add_embedding_with_id() {
        // given
        String id = randomUUID();
        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(id, embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(searchResult.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score()).isCloseTo(1, percentage());
        assertThat(match.embeddingId()).isEqualTo(id);
        if (assertEmbedding()) {
            assertThat(match.embedding()).isEqualTo(embedding);
        }
        assertThat(match.embedded()).isNotNull();
    }

    @Override
    @Test
    protected void should_add_embedding() {

        // given
        Embedding embedding = embeddingModel().embed("hello").content();
        String id = embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(id).isNotBlank();

        assertThat(searchResult.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score()).isCloseTo(1, percentage());
        assertThat(match.embeddingId()).isEqualTo(id);
        if (assertEmbedding()) {
            assertThat(match.embedding()).isEqualTo(embedding);
        }
        assertThat(match.embedded()).isNotNull();
    }

    @Override
    @Test
    protected void should_add_multiple_embeddings() {
        // given
        Embedding firstEmbedding = embeddingModel().embed("hello").content();
        Embedding secondEmbedding = embeddingModel().embed("hi").content();
        List<String> ids = embeddingStore().addAll(asList(firstEmbedding, secondEmbedding));
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .build();

        // when
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(searchRequest);

        // then
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotBlank();
        assertThat(ids.get(1)).isNotBlank();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));

        assertThat(searchResult.matches()).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = searchResult.matches().get(0);
        assertThat(firstMatch.score()).isCloseTo(1, percentage());
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        if (assertEmbedding()) {
            assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        }
        assertThat(firstMatch.embedded()).isNotNull();

        EmbeddingMatch<TextSegment> secondMatch = searchResult.matches().get(1);
        assertThat(secondMatch.score())
                .isCloseTo(
                        RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                        percentage());
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        if (assertEmbedding()) {
            assertThat(CosineSimilarity.between(secondMatch.embedding(), secondEmbedding))
                    .isCloseTo(1, withPercentage(0.01));
        }
        assertThat(secondMatch.embedded()).isNotNull();
    }
}
