package dev.langchain4j.store.embedding.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HybridEmbeddingStoreTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    @Test
    void should_serialize_with_correct_json_structure() throws JsonProcessingException {
        // given
        String text = "Test serialization structure";
        TextSegment segment = TextSegment.from(text, Metadata.from("key", "value"));
        embeddingStore.add(embeddingModel.embed(text).content(), segment);

        // when
        String json = embeddingStore.serializeToJson();

        // then
        assertThat(json).isNotNull().isNotEmpty();

        // Parse JSON and validate structure
        JsonNode rootNode = OBJECT_MAPPER.readTree(json);

        // Verify top-level structure has exactly the expected keys
        assertThat(rootNode.has("parentData")).isTrue();
        assertThat(rootNode.has("hybridData")).isTrue();
        assertThat(rootNode.size()).isEqualTo(2); // Should only have these two keys

        // Verify parentData is a string (serialized JSON from parent)
        assertThat(rootNode.get("parentData").isTextual()).isTrue();

        // Verify hybridData is an object with expected structure
        JsonNode hybridDataNode = rootNode.get("hybridData");
        assertThat(hybridDataNode.isObject()).isTrue();
        assertThat(hybridDataNode.has("entryToFileMapping")).isTrue();
        assertThat(hybridDataNode.has("chunkStorageDirectory")).isTrue();
        assertThat(hybridDataNode.has("cacheSize")).isTrue();

        // Verify entryToFileMapping is an object
        assertThat(hybridDataNode.get("entryToFileMapping").isObject()).isTrue();

        // Verify chunkStorageDirectory is a string
        assertThat(hybridDataNode.get("chunkStorageDirectory").isTextual()).isTrue();

        // Verify cacheSize is a number
        assertThat(hybridDataNode.get("cacheSize").isNumber()).isTrue();
        assertThat(hybridDataNode.get("cacheSize").asInt()).isEqualTo(0); // Default cache size
    }

    @Test
    void should_deserialize_from_json_with_correct_structure() {
        // given
        String text = "Test deserialization structure";
        Metadata metadata = Metadata.from("category", "test");
        TextSegment segment = TextSegment.from(text, metadata);
        Embedding embedding = embeddingModel.embed(text).content();
        String id = embeddingStore.add(embedding, segment);

        String json = embeddingStore.serializeToJson();

        // when
        HybridEmbeddingStore<TextSegment> deserializedStore = HybridEmbeddingStore.fromJson(json);

        // then
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<TextSegment> result = deserializedStore.search(request);

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo(text);
        assertThat(result.matches().get(0).embedded().metadata().getString("category"))
                .isEqualTo("test");
    }

    @Test
    void should_round_trip_serialize_and_deserialize_preserving_data() {
        // given
        String text1 = "First document for serialization test";
        String text2 = "Second document for serialization test";

        Metadata metadata1 = Metadata.from(Map.of("type", "doc", "priority", "high"));
        Metadata metadata2 = Metadata.from(Map.of("type", "note", "priority", "low"));

        TextSegment segment1 = TextSegment.from(text1, metadata1);
        TextSegment segment2 = TextSegment.from(text2, metadata2);

        Embedding embedding1 = embeddingModel.embed(text1).content();
        Embedding embedding2 = embeddingModel.embed(text2).content();

        embeddingStore.add("custom-id-1", embedding1, segment1);
        embeddingStore.add("custom-id-2", embedding2, segment2);

        // when - serialize and deserialize
        String json = embeddingStore.serializeToJson();
        HybridEmbeddingStore<TextSegment> newStore = HybridEmbeddingStore.fromJson(json);

        // then - verify all data is preserved
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding1)
                .maxResults(10)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<TextSegment> result = newStore.search(request);

        assertThat(result.matches()).hasSize(2);

        // Verify metadata is preserved
        Filter highPriorityFilter =
                MetadataFilterBuilder.metadataKey("priority").isEqualTo("high");
        EmbeddingSearchRequest filteredRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding1)
                .filter(highPriorityFilter)
                .maxResults(10)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<TextSegment> filteredResult = newStore.search(filteredRequest);

        assertThat(filteredResult.matches()).hasSize(1);
        assertThat(filteredResult.matches().get(0).embedded().text()).isEqualTo(text1);
    }

    @Test
    void should_serialize_file_and_deserialize_from_file() throws IOException {
        // given
        String text = "File serialization test";
        TextSegment segment = TextSegment.from(text, Metadata.from("source", "file"));
        Embedding embedding = embeddingModel.embed(text).content();
        embeddingStore.add(embedding, segment);

        Path serializedFile = temporaryDirectory.resolve("hybrid-store.json");

        // when
        embeddingStore.serializeToFile(serializedFile);
        HybridEmbeddingStore<TextSegment> loadedStore = HybridEmbeddingStore.fromFile(serializedFile);

        // then
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<TextSegment> result = loadedStore.search(request);

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo(text);
        assertThat(result.matches().get(0).embedded().metadata().getString("source"))
                .isEqualTo("file");
    }

    @Test
    void should_serialize_with_different_cache_sizes() throws JsonProcessingException {
        // given
        HybridEmbeddingStore<TextSegment> storeCacheSize5 = new HybridEmbeddingStore<>(temporaryDirectory, 5);
        HybridEmbeddingStore<TextSegment> storeCacheSize10 = new HybridEmbeddingStore<>(temporaryDirectory, 10);

        String text = "Cache size test";
        TextSegment segment = TextSegment.from(text);
        Embedding embedding = embeddingModel.embed(text).content();

        storeCacheSize5.add(embedding, segment);
        storeCacheSize10.add(embedding, segment);

        // when
        String json5 = storeCacheSize5.serializeToJson();
        String json10 = storeCacheSize10.serializeToJson();

        // then - Parse JSON and verify cache size values
        JsonNode rootNode5 = OBJECT_MAPPER.readTree(json5);
        JsonNode rootNode10 = OBJECT_MAPPER.readTree(json10);

        JsonNode hybridData5 = rootNode5.get("hybridData");
        JsonNode hybridData10 = rootNode10.get("hybridData");

        assertThat(hybridData5.get("cacheSize").asInt()).isEqualTo(5);
        assertThat(hybridData10.get("cacheSize").asInt()).isEqualTo(10);

        // Verify deserialization preserves cache size
        HybridEmbeddingStore<TextSegment> restored5 = HybridEmbeddingStore.fromJson(json5);
        HybridEmbeddingStore<TextSegment> restored10 = HybridEmbeddingStore.fromJson(json10);

        // Both should work correctly regardless of cache size
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .minScore(0.0)
                .build();

        EmbeddingSearchResult<TextSegment> result5 = restored5.search(request);
        EmbeddingSearchResult<TextSegment> result10 = restored10.search(request);

        assertThat(result5.matches()).hasSize(1);
        assertThat(result10.matches()).hasSize(1);
    }

    @Test
    void should_handle_serialization_with_empty_store() throws JsonProcessingException {
        // given - empty store

        // when
        String json = embeddingStore.serializeToJson();

        // then
        assertThat(json).isNotNull().isNotEmpty();

        // Parse JSON and validate structure for empty store
        JsonNode rootNode = OBJECT_MAPPER.readTree(json);

        assertThat(rootNode.has("parentData")).isTrue();
        assertThat(rootNode.has("hybridData")).isTrue();

        JsonNode hybridDataNode = rootNode.get("hybridData");
        assertThat(hybridDataNode.has("entryToFileMapping")).isTrue();
        assertThat(hybridDataNode.has("chunkStorageDirectory")).isTrue();
        assertThat(hybridDataNode.has("cacheSize")).isTrue();

        // Verify empty store has empty entryToFileMapping
        JsonNode entryToFileMappingNode = hybridDataNode.get("entryToFileMapping");
        assertThat(entryToFileMappingNode.isObject()).isTrue();
        assertThat(entryToFileMappingNode.size()).isEqualTo(0);

        // Verify empty store can be deserialized
        HybridEmbeddingStore<TextSegment> deserializedStore = HybridEmbeddingStore.fromJson(json);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("any query").content())
                .maxResults(10)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<TextSegment> result = deserializedStore.search(request);

        assertThat(result.matches()).isEmpty();
    }

    @Test
    void should_validate_complete_json_structure_and_parentData_format() throws JsonProcessingException {
        // given
        String text1 = "First test document";
        String text2 = "Second test document";

        Metadata metadata1 = Metadata.from("category", "test1");
        Metadata metadata2 = Metadata.from("category", "test2");

        TextSegment segment1 = TextSegment.from(text1, metadata1);
        TextSegment segment2 = TextSegment.from(text2, metadata2);

        embeddingStore.add("id1", embeddingModel.embed(text1).content(), segment1);
        embeddingStore.add("id2", embeddingModel.embed(text2).content(), segment2);

        // when
        String json = embeddingStore.serializeToJson();

        // then
        JsonNode rootNode = OBJECT_MAPPER.readTree(json);

        // Validate top-level structure
        assertThat(rootNode.isObject()).isTrue();
        assertThat(rootNode.size()).isEqualTo(2);
        assertThat(rootNode.has("parentData")).isTrue();
        assertThat(rootNode.has("hybridData")).isTrue();

        // Validate parentData is valid JSON string
        String parentDataJson = rootNode.get("parentData").asText();
        assertThat(parentDataJson).isNotNull().isNotEmpty();

        // Parse parentData to ensure it's valid JSON
        JsonNode parentDataNode = OBJECT_MAPPER.readTree(parentDataJson);
        assertThat(parentDataNode.isObject()).isTrue();
        assertThat(parentDataNode.has("entries")).isTrue();

        // Validate parentData entries structure
        JsonNode entriesNode = parentDataNode.get("entries");
        assertThat(entriesNode.isArray()).isTrue();
        assertThat(entriesNode.size()).isEqualTo(2);

        // Validate hybridData structure
        JsonNode hybridDataNode = rootNode.get("hybridData");
        assertThat(hybridDataNode.isObject()).isTrue();
        assertThat(hybridDataNode.size()).isEqualTo(3);

        // Validate entryToFileMapping
        JsonNode entryToFileMappingNode = hybridDataNode.get("entryToFileMapping");
        assertThat(entryToFileMappingNode.isObject()).isTrue();
        assertThat(entryToFileMappingNode.size()).isEqualTo(2);
        assertThat(entryToFileMappingNode.has("id1")).isTrue();
        assertThat(entryToFileMappingNode.has("id2")).isTrue();
        assertThat(entryToFileMappingNode.get("id1").asText()).endsWith(".json");
        assertThat(entryToFileMappingNode.get("id2").asText()).endsWith(".json");

        // Validate chunkStorageDirectory
        JsonNode chunkStorageDirNode = hybridDataNode.get("chunkStorageDirectory");
        assertThat(chunkStorageDirNode.isTextual()).isTrue();
        assertThat(chunkStorageDirNode.asText()).isNotEmpty();

        // Validate cacheSize
        JsonNode cacheSizeNode = hybridDataNode.get("cacheSize");
        assertThat(cacheSizeNode.isNumber()).isTrue();
        assertThat(cacheSizeNode.asInt()).isEqualTo(0);
    }

    @Test
    void should_ensure_parentData_and_hybridData_constants_are_used_consistently() throws JsonProcessingException {
        // given
        embeddingStore.add(embeddingModel.embed("test").content(), TextSegment.from("test"));

        // when
        String json = embeddingStore.serializeToJson();

        // then - Parse as generic JSON to verify exact key names
        JsonNode rootNode = OBJECT_MAPPER.readTree(json);

        // Verify the exact keys match our constants (not just that they contain the strings)
        String[] actualKeys = new String[rootNode.size()];
        int index = 0;
        for (String fieldName : (Iterable<String>) () -> rootNode.fieldNames()) {
            actualKeys[index++] = fieldName;
        }

        assertThat(actualKeys).containsExactlyInAnyOrder("parentData", "hybridData");

        // Test deserialization to ensure the constants work both ways
        HybridEmbeddingStore<TextSegment> deserializedStore = HybridEmbeddingStore.fromJson(json);
        assertThat(deserializedStore).isNotNull();

        // Verify functionality is preserved
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("test").content())
                .maxResults(1)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<TextSegment> result = deserializedStore.search(request);

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).embedded().text()).isEqualTo("test");
    }
}
