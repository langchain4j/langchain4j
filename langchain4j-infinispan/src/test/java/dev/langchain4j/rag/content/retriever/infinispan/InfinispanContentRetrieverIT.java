package dev.langchain4j.rag.content.retriever.infinispan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.infinispan.testcontainers.InfinispanContainer.IMAGE_BASENAME;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.infinispan.InfinispanEmbeddingStore;
import dev.langchain4j.store.embedding.infinispan.InfinispanStoreConfiguration;
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

        retriever = InfinispanContentRetriever.builder()
                .cacheName(CACHE_NAME)
                .dimension(embeddingModel.dimension())
                .distance(100)
                .infinispanConfigBuilder(builder)
                .maxResults(10)
                .build();
        retriever.remoteCache().clear();

        ConfigurationBuilder storeBuilder = new ConfigurationBuilder();
        storeBuilder.uri(infinispan.getConnectionURI());
        storeBuilder.socketTimeout(5000);
        storeBuilder.maxRetries(10);

        embeddingStore = InfinispanEmbeddingStore.builder()
                .cacheName(CACHE_NAME)
                .dimension(embeddingModel.dimension())
                .distance(100)
                .textFieldAnnotation(InfinispanStoreConfiguration.TEXT_FIELD_ANNOTATION_FULLTEXT)
                .infinispanConfigBuilder(storeBuilder)
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
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.uri(infinispan.getConnectionURI());
        builder.socketTimeout(5000);
        builder.maxRetries(10);

        InfinispanContentRetriever limitedRetriever = InfinispanContentRetriever.builder()
                .cacheName(CACHE_NAME)
                .dimension(embeddingModel.dimension())
                .distance(100)
                .infinispanConfigBuilder(builder)
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

        List<Content> results = retriever.retrieveAsync(new Query("Infinispan")).get();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().text()).contains("Infinispan");
    }

    @Test
    void should_preserve_metadata() {
        TextSegment segment = TextSegment.from(
                "Infinispan supports clustering", dev.langchain4j.data.document.Metadata.from("category", "database"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();
        embeddingStore.add(embedding, segment);

        List<Content> results = retriever.retrieve(new Query("clustering"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().metadata().getString("category"))
                .isEqualTo("database");
    }

    private void addDocuments(List<String> texts) {
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
    }
}
