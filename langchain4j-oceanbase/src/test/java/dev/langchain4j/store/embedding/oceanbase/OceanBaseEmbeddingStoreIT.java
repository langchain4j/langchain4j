package dev.langchain4j.store.embedding.oceanbase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.within;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Integration test for OceanBaseEmbeddingStore.
 */
public class OceanBaseEmbeddingStoreIT {

    private OceanBaseEmbeddingStore embeddingStore;

    @BeforeEach
    void setUp() {
        embeddingStore = CommonTestOperations.newEmbeddingStore();
    }

    @BeforeAll
    static void setUpAll() {
        // The container is started automatically in CommonTestOperations static block
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        try {
            CommonTestOperations.dropTable();
        } finally {
            CommonTestOperations.stopContainer();
        }
    }

    @Test
    void should_add_embedding_and_find_it_by_similarity() {
        // Given
        Embedding embedding = TestData.randomEmbedding();

        // When
        String id = embeddingStore.add(embedding);

        // Then
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build())
                .matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embeddingId()).isEqualTo(id);
        assertThat(matches.get(0).embedding().vector()).usingComparatorWithPrecision(0.0001f).containsExactly(embedding.vector());
        assertThat(matches.get(0).score()).isCloseTo(1.0, within(0.01));
    }

    @Test
    void should_add_embedding_with_text_segment_and_find_it_by_similarity() {
        // Given
        Embedding embedding = TestData.randomEmbedding();
        TextSegment segment = TextSegment.from("Test text", Metadata.from("key", "value"));

        // When
        String id = embeddingStore.add(embedding, segment);

        // Then
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build())
                .matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embeddingId()).isEqualTo(id);
        assertThat(matches.get(0).embedding().vector()).usingComparatorWithPrecision(0.0001f).containsExactly(embedding.vector());
        assertThat(matches.get(0).embedded().text()).isEqualTo("Test text");
        assertThat(matches.get(0).embedded().metadata().getString("key")).isEqualTo("value");
    }

    @Test
    void should_add_multiple_embeddings_and_find_them_by_similarity() {
        // Given
        Embedding[] embeddings = TestData.sampleEmbeddings();
        TextSegment[] segments = TestData.sampleTextSegments();

        // When
        List<String> ids = embeddingStore.addAll(Arrays.asList(embeddings), Arrays.asList(segments));

        // Then
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(TestData.queryEmbedding())
                                .maxResults(3)
                                .build())
                .matches();

        assertThat(matches).hasSize(3);

        // The query vector [0.9, 1.0, 0.9] should be closest to fruits
        // Order should be: orange, banana, apple
        assertThat(matches.get(0).embedded().text()).isEqualTo("橙子");
        assertThat(matches.get(1).embedded().text()).isEqualTo("香蕉");
        assertThat(matches.get(2).embedded().text()).isEqualTo("苹果");
    }

    @Test
    void should_remove_embeddings_by_collection() {
        // Given
        Embedding[] embeddings = TestData.sampleEmbeddings();
        TextSegment[] segments = TestData.sampleTextSegments();
        List<String> ids = embeddingStore.addAll(Arrays.asList(embeddings), Arrays.asList(segments));

        // When - remove first 3 embeddings (fruits)
        embeddingStore.removeAll(ids.subList(0, 3));

        // Then - only vegetables should remain
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(TestData.queryEmbedding())
                                .maxResults(10)
                                .build())
                .matches();

        assertThat(matches).hasSize(3);
        assertThat(matches).extracting(match -> match.embedded().metadata().getString("type"))
                .containsOnly("vegetable");
    }

    @Test
    void should_remove_embeddings_by_filter() {
        // Given
        Embedding[] embeddings = TestData.sampleEmbeddings();
        TextSegment[] segments = TestData.sampleTextSegments();
        embeddingStore.addAll(Arrays.asList(embeddings), Arrays.asList(segments));

        // When - remove all fruits
        embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("type").isEqualTo("fruit"));

        // Then - only vegetables should remain
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(TestData.queryEmbedding())
                                .maxResults(10)
                                .build())
                .matches();

        assertThat(matches).hasSize(3);
        assertThat(matches).extracting(match -> match.embedded().metadata().getString("type"))
                .containsOnly("vegetable");
    }

    @Test
    void should_filter_by_metadata() {
        // Given
        Embedding[] embeddings = TestData.sampleEmbeddings();
        TextSegment[] segments = TestData.sampleTextSegments();
        embeddingStore.addAll(Arrays.asList(embeddings), Arrays.asList(segments));

        // When: filter by fruits only
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(TestData.queryEmbedding())
                                .maxResults(6)
                                .filter(MetadataFilterBuilder.metadataKey("type").isEqualTo("fruit"))
                                .build())
                .matches();

        // Then
        assertThat(matches).hasSize(3);
        assertThat(matches).extracting(match -> match.embedded().metadata().getString("type"))
                .containsOnly("fruit");

        // When: filter by red color
        matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(TestData.queryEmbedding())
                                .maxResults(6)
                                .filter(MetadataFilterBuilder.metadataKey("color").isEqualTo("red"))
                                .build())
                .matches();

        // Then
        assertThat(matches).hasSize(2);
        assertThat(matches).extracting(match -> match.embedded().metadata().getString("color"))
                .containsOnly("red");
    }

    @Test
    void should_remove_embedding() {
        // Given
        Embedding embedding = TestData.randomEmbedding();
        String id = embeddingStore.add(embedding);

        // When
        embeddingStore.remove(id);

        // Then
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(embedding)
                                .maxResults(1)
                                .build())
                .matches();

        assertThat(matches).isEmpty();
    }

    @Test
    void should_remove_all_embeddings() {
        // Given
        Embedding[] embeddings = TestData.sampleEmbeddings();
        TextSegment[] segments = TestData.sampleTextSegments();
        embeddingStore.addAll(Arrays.asList(embeddings), Arrays.asList(segments));

        // When
        embeddingStore.removeAll();

        // Then
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(TestData.queryEmbedding())
                                .maxResults(10)
                                .build())
                .matches();

        assertThat(matches).isEmpty();
    }
}
