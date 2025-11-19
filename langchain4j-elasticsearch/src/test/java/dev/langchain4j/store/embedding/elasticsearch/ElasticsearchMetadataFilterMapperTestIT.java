package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ElasticsearchMetadataFilterMapperTestIT {

    static ElasticsearchClientHelper elasticsearchClientHelper = new ElasticsearchClientHelper();
    String indexName;

    @BeforeAll
    static void startServices() throws IOException {
        elasticsearchClientHelper.startServices();
        assertThat(elasticsearchClientHelper.restClient).isNotNull();
        assertThat(elasticsearchClientHelper.client).isNotNull();
    }

    @AfterAll
    static void stopServices() throws IOException {
        elasticsearchClientHelper.stopServices();
    }

    @BeforeEach
    void createEmbeddingStore() {
        indexName = randomUUID();
    }

    @AfterEach
    void removeDataStore() throws IOException {
        // We remove the indices in case we were running with a local test instance
        // we don't keep dirty things around
        elasticsearchClientHelper.removeDataStore(indexName);
    }

    /**
     * test like operator filter
     */
    @ParameterizedTest
    @MethodSource("filters")
    void should_use_like_operator_to_filter(Filter filter, String expectedPlanet) throws Exception {

        TextSegment segmentVenus = TextSegment.from(
                "It's like earth but closest to the sun",
                metadata("planet", "Venus").put("type", "Earth-sized"));
        TextSegment segmentMars =
                TextSegment.from("It's like earth", metadata("planet", "Mars").put("type", "Sub-Earth"));

        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        // configuration is irrelevant
        EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
                .configuration(ElasticsearchConfigurationKnn.builder()
                        .numCandidates(10)
                        .build())
                .restClient(elasticsearchClientHelper.restClient)
                .indexName(indexName)
                .build();

        embeddingStore.add(embeddingModel.embed(segmentVenus).content(), segmentVenus);
        embeddingStore.add(embeddingModel.embed(segmentMars).content(), segmentMars);

        // needed to avoid search delays and test failures
        elasticsearchClientHelper.refreshIndex(indexName);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .filter(filter)
                .build();

        List<Content> retrieved = contentRetriever.retrieve(Query.from("Recommend me a planet like earth"));

        assertThat(retrieved)
                .as("Expected at least one result for filter " + filter)
                .isNotEmpty();

        assertThat(retrieved.get(0).textSegment().metadata().getString("planet"))
                .isEqualTo(expectedPlanet);
    }

    static Stream<Arguments> filters() {
        return Stream.of(
                // LIKE %_rs -  only Mars remains
                Arguments.of(metadataKey("planet").like("%_rs", false), "Mars"),
                // Not LIKE %_rs  -  only Venus remains
                Arguments.of(metadataKey("planet").like("%_rs", true), "Venus"));
    }
}
