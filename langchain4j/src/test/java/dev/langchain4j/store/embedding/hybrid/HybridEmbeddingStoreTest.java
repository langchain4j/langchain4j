package dev.langchain4j.store.embedding.hybrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HybridEmbeddingStoreTest {

    @TempDir
    Path temporaryDirectory;

    private EmbeddingModel embeddingModel;
    private HybridEmbeddingStore<TextSegment> embeddingStore;

    @BeforeEach
    void setUp() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        embeddingStore = new HybridEmbeddingStore<>(temporaryDirectory);
    }

    @AfterEach
    void tearDown() {
        if (embeddingStore != null) {
            embeddingStore.removeAll();
        }
    }

    @Test
    void should_create_with_default_constructor() {
        // when
        HybridEmbeddingStore<TextSegment> store = new HybridEmbeddingStore<>();

        // then
        assertThat(store).isNotNull();
        // Default constructor should create a temporary directory
    }

    @Test
    void should_create_with_custom_directory() {
        // given
        Path customDir = temporaryDirectory.resolve("custom");

        // when
        HybridEmbeddingStore<TextSegment> store = new HybridEmbeddingStore<>(customDir);

        // then
        assertThat(store).isNotNull();
        assertThat(Files.exists(customDir)).isTrue();
    }

    @Test
    void should_create_with_cache_size() {
        // given
        int cacheSize = 10;

        // when
        HybridEmbeddingStore<TextSegment> store = new HybridEmbeddingStore<>(temporaryDirectory, cacheSize);

        // then
        assertThat(store).isNotNull();
    }

    @Test
    void should_add_embedding_without_embedded_content() {
        // given
        Embedding embedding = embeddingModel.embed("Hello world").content();

        // when
        String id = embeddingStore.add(embedding);

        // then
        assertThat(id).isNotNull().isNotEmpty();
    }

    @Test
    void should_add_embedding_with_embedded_content() {
        // given
        String text = "Hello world";
        TextSegment segment = TextSegment.from(text);
        Embedding embedding = embeddingModel.embed(text).content();

        // when
        String id = embeddingStore.add(embedding, segment);

        // then
        assertThat(id).isNotNull().isNotEmpty();

        // Verify the chunk file was created
        Path chunkFile = temporaryDirectory.resolve(id + ".json");
        assertThat(Files.exists(chunkFile)).isTrue();
    }

    @Test
    void should_add_embedding_with_id() {
        // given
        String customId = "custom-id-123";
        String text = "Hello world";
        TextSegment segment = TextSegment.from(text);
        Embedding embedding = embeddingModel.embed(text).content();

        // when
        embeddingStore.add(customId, embedding, segment);

        // then
        Path chunkFile = temporaryDirectory.resolve(customId + ".json");
        assertThat(Files.exists(chunkFile)).isTrue();
    }

    @Test
    void should_add_embedding_with_metadata() {
        // given
        String text = "Hello world";
        Metadata metadata = Metadata.from("category", "greeting");
        TextSegment segment = TextSegment.from(text, metadata);
        Embedding embedding = embeddingModel.embed(text).content();

        // when
        String id = embeddingStore.add(embedding, segment);

        // then
        assertThat(id).isNotNull();
        Path chunkFile = temporaryDirectory.resolve(id + ".json");
        assertThat(Files.exists(chunkFile)).isTrue();
    }

    @Test
    void should_add_multiple_embeddings() {
        // given
        Embedding embedding1 = embeddingModel.embed("First text").content();
        Embedding embedding2 = embeddingModel.embed("Second text").content();
        List<Embedding> embeddings = Arrays.asList(embedding1, embedding2);

        // when
        List<String> ids = embeddingStore.addAll(embeddings);

        // then
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotNull().isNotEmpty();
        assertThat(ids.get(1)).isNotNull().isNotEmpty();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
    }

    @Test
    void should_add_multiple_embeddings_with_content() {
        // given
        List<String> ids = Arrays.asList("id1", "id2");
        List<String> texts = Arrays.asList("First text", "Second text");
        List<Embedding> embeddings = Arrays.asList(
                embeddingModel.embed(texts.get(0)).content(),
                embeddingModel.embed(texts.get(1)).content());
        List<TextSegment> segments = Arrays.asList(TextSegment.from(texts.get(0)), TextSegment.from(texts.get(1)));

        // when
        embeddingStore.addAll(ids, embeddings, segments);

        // then
        assertThat(Files.exists(temporaryDirectory.resolve("id1.json"))).isTrue();
        assertThat(Files.exists(temporaryDirectory.resolve("id2.json"))).isTrue();
    }

    @Test
    void should_search_for_similar_embeddings() {
        // given
        String text1 = "Java programming language";
        String text2 = "Python programming language";
        String text3 = "Weather is nice today";

        embeddingStore.add(embeddingModel.embed(text1).content(), TextSegment.from(text1));
        embeddingStore.add(embeddingModel.embed(text2).content(), TextSegment.from(text2));
        embeddingStore.add(embeddingModel.embed(text3).content(), TextSegment.from(text3));

        Embedding queryEmbedding = embeddingModel.embed("programming languages").content();

        // when
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(2)
                .minScore(0.1)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        // then
        assertThat(result.matches()).hasSize(2);
        assertThat(result.matches().get(0).embedded().text()).contains("programming");
        assertThat(result.matches().get(1).embedded().text()).contains("programming");
    }

    @Test
    void should_search_with_metadata_filter() {
        // given
        Metadata metadata1 = Metadata.from("category", "tech");
        Metadata metadata2 = Metadata.from("category", "weather");

        String text1 = "Java programming";
        String text2 = "Weather report";

        embeddingStore.add(embeddingModel.embed(text1).content(), TextSegment.from(text1, metadata1));
        embeddingStore.add(embeddingModel.embed(text2).content(), TextSegment.from(text2, metadata2));

        Embedding queryEmbedding = embeddingModel.embed("programming").content();
        Filter filter = MetadataFilterBuilder.metadataKey("category").isEqualTo("tech");

        // when
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .minScore(0.0)
                .filter(filter)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        // then
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo(text1);
    }

    @Test
    void should_remove_embeddings_by_ids() {
        // given
        String text1 = "First text";
        String text2 = "Second text";
        String id1 = embeddingStore.add(embeddingModel.embed(text1).content(), TextSegment.from(text1));
        String id2 = embeddingStore.add(embeddingModel.embed(text2).content(), TextSegment.from(text2));

        // when
        embeddingStore.removeAll(Collections.singletonList(id1));

        // then
        assertThat(Files.exists(temporaryDirectory.resolve(id1 + ".json"))).isFalse();
        assertThat(Files.exists(temporaryDirectory.resolve(id2 + ".json"))).isTrue();
    }

    @Test
    void should_remove_embeddings_by_filter() {
        // given
        Metadata metadata1 = Metadata.from("category", "tech");
        Metadata metadata2 = Metadata.from("category", "weather");

        String text1 = "Java programming";
        String text2 = "Weather report";

        String id1 = embeddingStore.add(embeddingModel.embed(text1).content(), TextSegment.from(text1, metadata1));
        String id2 = embeddingStore.add(embeddingModel.embed(text2).content(), TextSegment.from(text2, metadata2));

        Filter filter = MetadataFilterBuilder.metadataKey("category").isEqualTo("tech");

        // when
        embeddingStore.removeAll(filter);

        // then
        assertThat(Files.exists(temporaryDirectory.resolve(id1 + ".json"))).isFalse();
        assertThat(Files.exists(temporaryDirectory.resolve(id2 + ".json"))).isTrue();
    }

    @Test
    void should_remove_all_embeddings() {
        // given
        String text1 = "First text";
        String text2 = "Second text";
        String id1 = embeddingStore.add(embeddingModel.embed(text1).content(), TextSegment.from(text1));
        String id2 = embeddingStore.add(embeddingModel.embed(text2).content(), TextSegment.from(text2));

        // when
        embeddingStore.removeAll();

        // then
        assertThat(Files.exists(temporaryDirectory.resolve(id1 + ".json"))).isFalse();
        assertThat(Files.exists(temporaryDirectory.resolve(id2 + ".json"))).isFalse();
    }

    @Test
    void should_serialize_to_json() {
        // given
        String text = "Hello world";
        TextSegment segment = TextSegment.from(text);
        embeddingStore.add(embeddingModel.embed(text).content(), segment);

        // when
        String json = embeddingStore.serializeToJson();

        // then
        assertThat(json).isNotNull().isNotEmpty();
        assertThat(json).contains("entries");
        assertThat(json).contains("chunkStorageDirectory");
        assertThat(json).contains("cacheSize");
    }

    @Test
    void should_serialize_to_file() throws IOException {
        // given
        String text = "Hello world";
        TextSegment segment = TextSegment.from(text);
        embeddingStore.add(embeddingModel.embed(text).content(), segment);

        Path serializedFile = temporaryDirectory.resolve("store.json");

        // when
        embeddingStore.serializeToFile(serializedFile);

        // then
        assertThat(Files.exists(serializedFile)).isTrue();
        String content = Files.readString(serializedFile);
        assertThat(content).contains("entries");
    }

    @Test
    void should_serialize_to_file_with_string_path() throws IOException {
        // given
        String text = "Hello world";
        TextSegment segment = TextSegment.from(text);
        embeddingStore.add(embeddingModel.embed(text).content(), segment);

        String serializedFilePath = temporaryDirectory.resolve("store.json").toString();

        // when
        embeddingStore.serializeToFile(serializedFilePath);

        // then
        assertThat(Files.exists(Path.of(serializedFilePath))).isTrue();
    }

    @Test
    void should_configure_chunk_storage_directory() {
        // given
        Path newDirectory = temporaryDirectory.resolve("new-storage");

        // when
        HybridEmbeddingStore<TextSegment> newStore = embeddingStore.withChunkStorageDirectory(newDirectory);

        // then
        assertThat(newStore).isNotNull();
        assertThat(Files.exists(newDirectory)).isTrue();
    }

    @Test
    void should_handle_cache_functionality() {
        // given
        HybridEmbeddingStore<TextSegment> cachedStore = new HybridEmbeddingStore<>(temporaryDirectory, 5);
        String text = "Hello world";
        TextSegment segment = TextSegment.from(text);
        Embedding embedding = embeddingModel.embed(text).content();

        // when
        String id = cachedStore.add(embedding, segment);

        // then - should not throw exceptions and work normally
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<TextSegment> result = cachedStore.search(request);

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo(text);
    }

    @Test
    void should_handle_search_with_no_results() {
        // given
        String text = "Hello world";
        embeddingStore.add(embeddingModel.embed(text).content(), TextSegment.from(text));

        Embedding queryEmbedding =
                embeddingModel.embed("completely different topic").content();

        // when
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .minScore(0.9) // Very high threshold
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        // then
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void should_handle_empty_store_search() {
        // given
        Embedding queryEmbedding = embeddingModel.embed("any query").content();

        // when
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        // then
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void should_handle_invalid_size_lists_in_addAll() {
        // given
        List<String> ids = Arrays.asList("id1", "id2");
        List<Embedding> embeddings = Arrays.asList(embeddingModel.embed("text1").content());
        List<TextSegment> segments = Arrays.asList(TextSegment.from("text1"));

        // when & then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> embeddingStore.addAll(ids, embeddings, segments))
                .withMessageContaining("same size");
    }
}
