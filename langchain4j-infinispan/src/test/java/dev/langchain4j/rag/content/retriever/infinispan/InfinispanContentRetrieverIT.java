package dev.langchain4j.rag.content.retriever.infinispan;

import static dev.langchain4j.rag.content.ContentMetadata.EMBEDDING_ID;
import static dev.langchain4j.rag.content.ContentMetadata.SCORE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.infinispan.testcontainers.InfinispanContainer.IMAGE_BASENAME;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.infinispan.InfinispanEmbeddingStore;
import dev.langchain4j.store.embedding.infinispan.InfinispanStoreConfiguration;
import java.time.Duration;
import java.util.List;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.util.Version;
import org.infinispan.testcontainers.InfinispanContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InfinispanContentRetrieverIT {

    static InfinispanContainer infinispan = new InfinispanContainer(IMAGE_BASENAME + ":" + Version.getVersion());
    static final String CACHE_NAME = "content-retriever-cache";
    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    InfinispanContentRetriever retriever;
    InfinispanEmbeddingStore embeddingStore;

    @BeforeAll
    static void beforeAll() {
        infinispan.start();
    }

    @AfterAll
    static void afterAll() {
        infinispan.stop();
    }

    @BeforeEach
    void setUp() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.uri(infinispan.getConnectionURI());
        builder.socketTimeout(5000);
        builder.maxRetries(10);

        embeddingStore = InfinispanEmbeddingStore.builder()
                .cacheName(CACHE_NAME)
                .dimension(embeddingModel.dimension())
                .distance(100)
                .textFieldAnnotation(InfinispanStoreConfiguration.TEXT_FIELD_ANNOTATION_FULLTEXT)
                .infinispanConfigBuilder(builder)
                .build();
        embeddingStore.clearCache();

        retriever = InfinispanContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .maxResults(10)
                .build();
    }

    @Test
    void should_retrieve_content_by_full_text_search() {
        addDocuments(List.of(
                "Infinispan is a distributed in-memory key-value data store",
                "Java is a programming language",
                "Infinispan supports full-text search and vector search"));

        List<Content> results = retriever.retrieve(new Query("Infinispan"));

        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results)
                .allSatisfy(content ->
                        assertThat(content.textSegment().text().toLowerCase()).contains("infinispan"));
    }

    @Test
    void should_return_empty_for_no_matches() {
        addDocuments(List.of("The sky is blue", "Roses are red"));

        List<Content> results = retriever.retrieve(new Query("Infinispan"));

        assertThat(results).isEmpty();
    }

    @Test
    void should_respect_max_results() {
        InfinispanContentRetriever limitedRetriever = InfinispanContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .maxResults(1)
                .build();

        addDocuments(List.of(
                "Java is great for building applications",
                "Java is used in enterprise software",
                "Java supports multithreading"));

        List<Content> results = limitedRetriever.retrieve(new Query("Java"));

        assertThat(results).hasSize(1);
    }

    @Test
    void should_retrieve_async() throws Exception {
        addDocuments(List.of("Infinispan is a distributed cache", "Redis is another cache solution"));

        List<Content> results = retriever.retrieveAsync(new Query("Infinispan")).get(30, SECONDS);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().text()).contains("Infinispan");
    }

    @Test
    void should_preserve_metadata() {
        TextSegment segment = TextSegment.from("Infinispan supports clustering", Metadata.from("category", "database"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();
        embeddingStore.add(embedding, segment);

        List<Content> results = retriever.retrieve(new Query("clustering"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().metadata().getString("category"))
                .isEqualTo("database");
    }

    @Test
    void should_return_score_and_id_sorted_by_relevance() {
        addDocuments(
                List.of("Infinispan supports full-text search and vector search", "Infinispan Infinispan Infinispan"));
        TextSegment segment = TextSegment.from("Infinispan is a distributed cache");
        String id = embeddingStore.add(embeddingModel.embed(segment.text()).content(), segment);

        List<Content> results = retriever.retrieve(new Query("Infinispan"));

        assertThat(results).hasSize(3);
        assertThat(results.get(0).textSegment().text()).isEqualTo("Infinispan Infinispan Infinispan");
        assertThat(results)
                .extracting(content -> (Double) content.metadata().get(SCORE))
                .allSatisfy(score -> assertThat(score).isPositive())
                .isSortedAccordingTo((a, b) -> Double.compare(b, a));
        assertThat(results)
                .filteredOn(content -> content.textSegment().text().equals(segment.text()))
                .singleElement()
                .satisfies(content ->
                        assertThat(content.metadata().get(EMBEDDING_ID)).isEqualTo(id));
    }

    @Test
    void should_treat_backslashes_in_the_query_as_text() {
        addDocuments(List.of("Java is a programming language", "Logs are stored in C:\\temp\\logs"));

        assertThat(retriever.retrieve(new Query("logs\\")))
                .singleElement()
                .satisfies(content -> assertThat(content.textSegment().text()).startsWith("Logs"));
    }

    @Test
    void should_match_multi_word_queries() {
        addDocuments(List.of(
                "Infinispan is a distributed in-memory key-value data store",
                "Java is a programming language",
                "Redis is another cache solution"));

        List<Content> results = retriever.retrieve(new Query("What is Infinispan?"));

        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
        assertThat(results.get(0).textSegment().text()).contains("Infinispan");
    }

    @Test
    void should_fail_async_retrieval_through_the_future() {
        assertThat(retriever.retrieveAsync(null))
                .failsWithin(Duration.ofSeconds(30))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(IllegalArgumentException.class)
                .withMessageContaining("query");
    }

    private void addDocuments(List<String> texts) {
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
    }
}
