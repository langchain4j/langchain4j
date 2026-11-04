package dev.langchain4j.store.embedding.milvus.generic;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.milvus.MilvusDatabaseKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

@Testcontainers
class MilvusHibernateEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    static final String MILVUS_DOCKER_IMAGE = System.getProperty("milvus.image", "milvusdb/milvus:v2.6.26");

    @Container
    static MilvusContainer databaseContainer =
            new MilvusContainer(MILVUS_DOCKER_IMAGE).withEnv("DEPLOY_MODE", "STANDALONE");

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private HibernateEmbeddingStore<?> embeddingStore;

    @Override
    protected void ensureStoreIsReady() {
        // The test container runs Milvus without authentication, so no credentials are configured
        embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
                .databaseKind(MilvusDatabaseKind.INSTANCE)
                .host(databaseContainer.getHost())
                .port(databaseContainer.getMappedPort(19530))
                .database("default")
                .table("test_" + UUID.randomUUID().toString().replace("-", ""))
                .dimension(embeddingModel.dimension())
                .createTable(true)
                .dropTableFirst(true)
                .build();
    }

    @AfterEach
    void clearData() {
        if (embeddingStore != null) {
            embeddingStore.close();
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @Test
    void should_find_an_embedding_right_after_adding_it() {
        // no wait between add() and search(): the default session consistency level of MilvusDatabaseKind
        // makes the search see the embedding that was just added
        Embedding embedding = embeddingModel.embed("hello").content();
        String id = embeddingStore.add(embedding, TextSegment.from("hello"));

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build());

        assertThat(result.matches()).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
    }

    @Test
    void should_score_matches_by_euclidean_distance() {
        HibernateEmbeddingStore<?> store = HibernateEmbeddingStore.dynamicBuilder()
                .databaseKind(MilvusDatabaseKind.INSTANCE)
                .host(databaseContainer.getHost())
                .port(databaseContainer.getMappedPort(19530))
                .database("default")
                .table("test_" + UUID.randomUUID().toString().replace("-", ""))
                .dimension(embeddingModel.dimension())
                .distanceFunction(DistanceFunction.EUCLIDEAN)
                .createTable(true)
                .dropTableFirst(true)
                .build();
        try {
            Embedding first = embeddingModel.embed("hello world").content();
            Embedding second =
                    embeddingModel.embed("completely unrelated text about cars").content();
            List<String> ids = store.addAll(
                    List.of(first, second), List.of(TextSegment.from("hello world"), TextSegment.from("cars")));

            List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(first)
                            .maxResults(2)
                            .build())
                    .matches();

            assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactlyElementsOf(ids);
            assertThat(matches.get(0).score()).isCloseTo(1, withPercentage(0.01));
            assertThat(matches.get(1).score())
                    .isCloseTo(1 / (1 + euclideanDistance(first, second)), withPercentage(0.01));

            double minScore = (matches.get(0).score() + matches.get(1).score()) / 2;
            List<EmbeddingMatch<TextSegment>> closeMatches = store.search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(first)
                            .maxResults(2)
                            .minScore(minScore)
                            .build())
                    .matches();

            assertThat(closeMatches).extracting(EmbeddingMatch::embeddingId).containsExactly(ids.get(0));
        } finally {
            store.close();
        }
    }

    private static double euclideanDistance(Embedding a, Embedding b) {
        double sum = 0;
        for (int i = 0; i < a.dimension(); i++) {
            double difference = a.vector()[i] - b.vector()[i];
            sum += difference * difference;
        }
        return Math.sqrt(sum);
    }

    @Test
    void should_use_explicit_database_kind_with_jdbc_url() {
        String jdbcUrl = "jdbc:milvus://" + databaseContainer.getHost() + ":" + databaseContainer.getMappedPort(19530)
                + "/default";
        HibernateEmbeddingStore<?> store = HibernateEmbeddingStore.dynamicBuilder()
                .jdbcUrl(jdbcUrl)
                .databaseKind(MilvusDatabaseKind.INSTANCE)
                .table("test_" + UUID.randomUUID().toString().replace("-", ""))
                .dimension(embeddingModel.dimension())
                .createTable(true)
                .dropTableFirst(true)
                .build();
        try {
            Embedding embedding = embeddingModel.embed("hello").content();
            String id = store.add(embedding, TextSegment.from("hello"));

            EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(1)
                    .build());

            assertThat(result.matches()).extracting(EmbeddingMatch::embeddingId).containsExactly(id);
        } finally {
            store.close();
        }
    }

    @Test
    void test_escape_in() {
        TextSegment[] segments = new TextSegment[] {
            TextSegment.from("toEscape", Metadata.from(Map.of("text", "This must be escaped '"))),
            TextSegment.from("notEscape", Metadata.from(Map.of("text", "This does not require to be escaped")))
        };
        List<Embedding> embeddings = new ArrayList<>(segments.length);
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddings.add(embedding);
        }

        List<String> ids = embeddingStore().addAll(embeddings, Arrays.asList(segments));
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSameSizeAs(segments));

        // In filter escapes values as well
        Filter filterIN = metadataKey("text").isIn("This must be escaped '");
        EmbeddingSearchRequest inSearchRequest = EmbeddingSearchRequest.builder()
                .maxResults(1)
                .queryEmbedding(embeddings.get(0))
                .filter(filterIN)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(inSearchRequest);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(ids.get(0));

        // In filter escapes values as well
        Filter filterNotIN = metadataKey("text").isNotIn("This must be escaped '");
        EmbeddingSearchRequest notInSearchRequest = EmbeddingSearchRequest.builder()
                .maxResults(1)
                .queryEmbedding(embeddings.get(0))
                .filter(filterNotIN)
                .build();

        searchResult = embeddingStore().search(notInSearchRequest);
        match = searchResult.matches().get(0);
        // It must retrieve the second embedding
        assertThat(match.embeddingId()).isEqualTo(ids.get(1));
    }
}
