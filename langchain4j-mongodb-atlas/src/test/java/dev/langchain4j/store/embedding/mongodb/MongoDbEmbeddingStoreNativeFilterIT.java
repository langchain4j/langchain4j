package dev.langchain4j.store.embedding.mongodb;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.Sets;

import java.time.Duration;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.data.Percentage.withPercentage;

class MongoDbEmbeddingStoreNativeFilterIT {

    static MongoDBAtlasContainer mongodb = new MongoDBAtlasContainer();

    static MongoClient client;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    IndexMapping indexMapping = IndexMapping.builder()
            .dimension(embeddingModel.dimension())
            .metadataFieldNames(Sets.newHashSet("test-key"))
            .build();

    EmbeddingStore<TextSegment> embeddingStore = MongoDbEmbeddingStore.builder()
            .fromClient(client)
            .databaseName("test_database")
            .collectionName("test_collection")
            .indexName("test_index")
            .filter(Filters.and(Filters.eqFull("metadata.test-key", "test-value")))
            .indexMapping(indexMapping)
            .createIndex(true)
            .build();

    @BeforeAll
    @SneakyThrows
    static void start() {
        mongodb.start();

        MongoCredential credential = MongoCredential.createCredential("root", "admin", "root".toCharArray());
        client = MongoClients.create(
                MongoClientSettings.builder()
                        .credential(credential)
                        .serverApi(ServerApi.builder().version(ServerApiVersion.V1).build())
                        .applyConnectionString(new ConnectionString(mongodb.getConnectionString()))
                        .build());
    }

    @BeforeEach
    void beforeEach() {
        // to avoid "cannot query search index while in state INITIAL_SYNC" error
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("dummy").content())
                .maxResults(1)
                .build();
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollDelay(Duration.ofSeconds(0))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThatNoException().isThrownBy(() -> embeddingStore.search(embeddingSearchRequest)));
    }

    @AfterAll
    static void stop() {
        mongodb.stop();
        client.close();
    }

    @Test
    void should_find_relevant_with_filter() {

        // given
        TextSegment segment = TextSegment.from("this segment should be found", Metadata.from("test-key", "test-value"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        TextSegment filterSegment = TextSegment.from("this segment should not be found", Metadata.from("test-key", "no-value"));
        Embedding filterEmbedding = embeddingModel.embed(filterSegment.text()).content();

        List<String> ids = embeddingStore.addAll(asList(embedding, filterEmbedding), asList(segment, filterSegment));
        assertThat(ids).hasSize(2);

        TextSegment refSegment = TextSegment.from("find a segment");
        Embedding refEmbedding = embeddingModel.embed(refSegment.text()).content();

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(refEmbedding, 2);

        // then
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(0.88, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(ids.get(0));
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);
    }

    @SneakyThrows
    private void awaitUntilPersisted() {
        Thread.sleep(2000);
    }
}
