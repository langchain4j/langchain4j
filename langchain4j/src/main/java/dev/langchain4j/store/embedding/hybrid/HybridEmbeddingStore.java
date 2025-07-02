package dev.langchain4j.store.embedding.hybrid;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Comparator.comparingDouble;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A hybrid {@link EmbeddingStore} that stores embeddings in memory and embedded content
 * (e.g., TextSegment) as files in a local directory.
 * <p>
 * This implementation provides a balance between performance and storage efficiency:
 * - Embeddings are kept in memory for fast similarity search
 * - Embedded content is stored as files on disk to save memory
 * - Includes optional LRU caching for recently loaded chunks
 * <p>
 * The store can be persisted using the {@link #serializeToJson()} and {@link #serializeToFile(Path)} methods.
 * It can also be recreated from JSON or a file using the {@link #fromJson(String)} and {@link #fromFile(Path)} methods.
 *
 * @param <Embedded> The class of the object that has been embedded.
 *                   Typically, it is {@link dev.langchain4j.data.segment.TextSegment}.
 */
public class HybridEmbeddingStore<Embedded> implements EmbeddingStore<Embedded> {

    private static final Logger log = LoggerFactory.getLogger(HybridEmbeddingStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private final CopyOnWriteArrayList<Entry<Embedded>> entries;
    private final Path chunkStorageDirectory;
    private final Map<String, Embedded> chunkCache;
    private final int cacheSize;

    /**
     * Creates a new HybridEmbeddingStore with default settings.
     * Uses a temporary directory for chunk storage and no caching.
     */
    public HybridEmbeddingStore() {
        this(createDefaultChunkDirectory(), 0);
    }

    /**
     * Creates a new HybridEmbeddingStore with specified chunk storage directory.
     *
     * @param chunkStorageDirectory Directory where embedded content will be stored as files
     */
    public HybridEmbeddingStore(Path chunkStorageDirectory) {
        this(chunkStorageDirectory, 0);
    }

    /**
     * Creates a new HybridEmbeddingStore with specified chunk storage directory and cache size.
     *
     * @param chunkStorageDirectory Directory where embedded content will be stored as files
     * @param cacheSize Size of LRU cache for recently loaded chunks (0 = no caching)
     */
    public HybridEmbeddingStore(Path chunkStorageDirectory, int cacheSize) {
        this.entries = new CopyOnWriteArrayList<>();
        this.chunkStorageDirectory = ensureNotNull(chunkStorageDirectory, "chunkStorageDirectory");
        this.cacheSize = Math.max(0, cacheSize);
        this.chunkCache = cacheSize > 0 ? createLRUCache(cacheSize) : new ConcurrentHashMap<>();
        createChunkStorageDirectory();
        log.debug(
                "Created HybridEmbeddingStore with storage directory: {} and cache size: {}",
                chunkStorageDirectory,
                cacheSize);
    }

    private HybridEmbeddingStore(Collection<Entry<Embedded>> entries, Path chunkStorageDirectory, int cacheSize) {
        this.entries = new CopyOnWriteArrayList<>(entries);
        this.chunkStorageDirectory = ensureNotNull(chunkStorageDirectory, "chunkStorageDirectory");
        this.cacheSize = Math.max(0, cacheSize);
        this.chunkCache = cacheSize > 0 ? createLRUCache(cacheSize) : new ConcurrentHashMap<>();
        createChunkStorageDirectory();
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        add(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, Embedded embedded) {
        String id = randomUUID();
        add(id, embedding, embedded);
        return id;
    }

    public void add(String id, Embedding embedding, Embedded embedded) {
        ensureNotBlank(id, "id");
        ensureNotNull(embedding, "embedding");

        String chunkFilePath = null;
        if (embedded != null) {
            chunkFilePath = saveChunkToFile(id, embedded);
        }

        entries.add(new Entry<>(id, embedding, chunkFilePath));
        log.debug("Added embedding with id: {} and chunk file: {}", id, chunkFilePath);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<Entry<Embedded>> newEntries = embeddings.stream()
                .map(embedding -> new Entry<Embedded>(randomUUID(), embedding, null))
                .collect(ArrayList::new, (list, entry) -> list.add(entry), ArrayList::addAll);

        return add(newEntries);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<Embedded> embedded) {
        if (ids.size() != embeddings.size() || embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("The list of ids and embeddings and embedded must have the same size");
        }

        List<Entry<Embedded>> newEntries = new ArrayList<>(ids.size());

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            Embedded embeddedContent = embedded.get(i);

            String chunkFilePath = null;
            if (embeddedContent != null) {
                chunkFilePath = saveChunkToFile(id, embeddedContent);
            }

            newEntries.add(new Entry<>(id, embedding, chunkFilePath));
        }
        add(newEntries);
    }

    private List<String> add(List<Entry<Embedded>> newEntries) {
        entries.addAll(newEntries);

        return newEntries.stream().map(entry -> entry.id).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        entries.removeIf(entry -> {
            if (ids.contains(entry.id)) {
                deleteChunkFile(entry.chunkFilePath);
                chunkCache.remove(entry.id);
                return true;
            }
            return false;
        });
        log.debug("Removed {} embeddings", ids.size());
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");

        entries.removeIf(entry -> {
            if (entry.chunkFilePath != null) {
                Embedded embedded = loadChunkFromFile(entry.chunkFilePath);
                if (embedded instanceof TextSegment) {
                    boolean matches = filter.test(((TextSegment) embedded).metadata());
                    if (matches) {
                        deleteChunkFile(entry.chunkFilePath);
                        chunkCache.remove(entry.id);
                    }
                    return matches;
                }
            }
            return false;
        });
        log.debug("Removed embeddings matching filter");
    }

    @Override
    public void removeAll() {
        // Delete all chunk files
        for (Entry<Embedded> entry : entries) {
            deleteChunkFile(entry.chunkFilePath);
        }
        entries.clear();
        chunkCache.clear();
        log.debug("Removed all embeddings and chunk files");
    }

    @Override
    public EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest embeddingSearchRequest) {
        Comparator<EmbeddingMatch<Embedded>> comparator = comparingDouble(EmbeddingMatch::score);
        PriorityQueue<EmbeddingMatch<Embedded>> matches = new PriorityQueue<>(comparator);

        Filter filter = embeddingSearchRequest.filter();

        for (Entry<Embedded> entry : entries) {
            Embedded embedded = null;

            // Load embedded content from file if needed for filtering
            if (filter != null && entry.chunkFilePath != null) {
                embedded = loadChunkFromFile(entry.chunkFilePath);
                if (embedded instanceof TextSegment) {
                    Metadata metadata = ((TextSegment) embedded).metadata();
                    if (!filter.test(metadata)) {
                        continue;
                    }
                }
            }

            double cosineSimilarity =
                    CosineSimilarity.between(entry.embedding, embeddingSearchRequest.queryEmbedding());
            double score = RelevanceScore.fromCosineSimilarity(cosineSimilarity);

            if (score >= embeddingSearchRequest.minScore()) {
                // Load embedded content if not already loaded
                if (embedded == null && entry.chunkFilePath != null) {
                    embedded = loadChunkFromFile(entry.chunkFilePath);
                }

                matches.add(new EmbeddingMatch<>(score, entry.id, entry.embedding, embedded));
                if (matches.size() > embeddingSearchRequest.maxResults()) {
                    matches.poll();
                }
            }
        }

        List<EmbeddingMatch<Embedded>> result = new ArrayList<>(matches);
        result.sort(comparator);
        Collections.reverse(result);

        log.debug("Found {} matches for search request", result.size());
        return new EmbeddingSearchResult<>(result);
    }

    /**
     * Configures the chunk storage base directory.
     *
     * @param chunkStorageDirectory New directory for storing chunk files
     * @return A new HybridEmbeddingStore instance with the specified directory
     */
    public HybridEmbeddingStore<Embedded> withChunkStorageDirectory(Path chunkStorageDirectory) {
        return new HybridEmbeddingStore<>(entries, chunkStorageDirectory, cacheSize);
    }

    /**
     * Serializes the store to JSON, including the in-memory index and chunk file paths.
     */
    public String serializeToJson() {
        try {
            HybridStoreData data = new HybridStoreData(entries, chunkStorageDirectory.toString(), cacheSize);
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize HybridEmbeddingStore to JSON", e);
        }
    }

    /**
     * Serializes the store to a file.
     */
    public void serializeToFile(Path filePath) {
        try {
            String json = serializeToJson();
            Files.write(filePath, json.getBytes(), CREATE, TRUNCATE_EXISTING);
            log.debug("Serialized store to file: {}", filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize HybridEmbeddingStore to file: " + filePath, e);
        }
    }

    /**
     * Serializes the store to a file.
     */
    public void serializeToFile(String filePath) {
        serializeToFile(Paths.get(filePath));
    }

    /**
     * Loads a store from JSON.
     */
    public static HybridEmbeddingStore<TextSegment> fromJson(String json) {
        try {
            HybridStoreData data = OBJECT_MAPPER.readValue(json, HybridStoreData.class);
            return new HybridEmbeddingStore<>(data.entries, Paths.get(data.chunkStorageDirectory), data.cacheSize);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize HybridEmbeddingStore from JSON", e);
        }
    }

    /**
     * Loads a store from a file.
     */
    public static HybridEmbeddingStore<TextSegment> fromFile(Path filePath) {
        try {
            String json = Files.readString(filePath);
            log.debug("Loaded store from file: {}", filePath);
            return fromJson(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load HybridEmbeddingStore from file: " + filePath, e);
        }
    }

    /**
     * Loads a store from a file.
     */
    public static HybridEmbeddingStore<TextSegment> fromFile(String filePath) {
        return fromFile(Paths.get(filePath));
    }

    private String saveChunkToFile(String id, Embedded embedded) {
        try {
            String fileName = id + ".json";
            Path filePath = chunkStorageDirectory.resolve(fileName);

            String content;
            if (embedded instanceof TextSegment) {
                // Custom serialization for TextSegment
                TextSegment textSegment = (TextSegment) embedded;
                ChunkData chunkData = new ChunkData(
                        textSegment.text(),
                        textSegment.metadata() != null ? textSegment.metadata().toMap() : null);
                content = OBJECT_MAPPER.writeValueAsString(chunkData);
            } else {
                // For other types, try default serialization
                content = OBJECT_MAPPER.writeValueAsString(embedded);
            }

            Files.write(filePath, content.getBytes(), CREATE, TRUNCATE_EXISTING);
            log.debug("Saved chunk to file: {}", filePath);
            return fileName;
        } catch (IOException e) {
            log.error("Failed to save chunk to file for id: {}", id, e);
            throw new RuntimeException("Failed to save chunk to file", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Embedded loadChunkFromFile(String chunkFilePath) {
        if (chunkFilePath == null) {
            return null;
        }

        // Check cache first
        String cacheKey = chunkFilePath;
        Embedded cached = chunkCache.get(cacheKey);
        if (cached != null) {
            log.debug("Loaded chunk from cache: {}", chunkFilePath);
            return cached;
        }

        try {
            Path filePath = chunkStorageDirectory.resolve(chunkFilePath);
            if (!Files.exists(filePath)) {
                log.warn("Chunk file does not exist: {}", filePath);
                return null;
            }

            String content = Files.readString(filePath);

            // Try to deserialize as ChunkData first (for TextSegment)
            try {
                ChunkData chunkData = OBJECT_MAPPER.readValue(content, ChunkData.class);
                Metadata metadata = chunkData.metadata != null ? Metadata.from(chunkData.metadata) : null;
                Embedded embedded = (Embedded) TextSegment.from(chunkData.text, metadata);

                // Add to cache
                chunkCache.put(cacheKey, embedded);
                log.debug("Loaded chunk from file: {}", filePath);
                return embedded;
            } catch (Exception e) {
                // Fall back to direct deserialization for other types
                Embedded embedded = (Embedded) OBJECT_MAPPER.readValue(content, TextSegment.class);

                // Add to cache
                chunkCache.put(cacheKey, embedded);
                log.debug("Loaded chunk from file (fallback): {}", filePath);
                return embedded;
            }
        } catch (IOException e) {
            log.error("Failed to load chunk from file: {}", chunkFilePath, e);
            return null;
        }
    }

    private void deleteChunkFile(String chunkFilePath) {
        if (chunkFilePath == null) {
            return;
        }

        try {
            Path filePath = chunkStorageDirectory.resolve(chunkFilePath);
            Files.deleteIfExists(filePath);
            log.debug("Deleted chunk file: {}", filePath);
        } catch (IOException e) {
            log.warn("Failed to delete chunk file: {}", chunkFilePath, e);
        }
    }

    private void createChunkStorageDirectory() {
        try {
            Files.createDirectories(chunkStorageDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create chunk storage directory: " + chunkStorageDirectory, e);
        }
    }

    private static Path createDefaultChunkDirectory() {
        try {
            return Files.createTempDirectory("hybrid-embedding-store");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create default chunk storage directory", e);
        }
    }

    private static <K, V> Map<K, V> createLRUCache(int maxSize) {
        return new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        };
    }

    static class Entry<Embedded> {
        String id;
        Embedding embedding;
        String chunkFilePath; // File path relative to chunk storage directory

        @JsonCreator
        Entry(
                @JsonProperty("id") String id,
                @JsonProperty("embedding") Embedding embedding,
                @JsonProperty("chunkFilePath") String chunkFilePath) {
            this.id = ensureNotBlank(id, "id");
            this.embedding = ensureNotNull(embedding, "embedding");
            this.chunkFilePath = chunkFilePath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry<?> that = (Entry<?>) o;
            return Objects.equals(this.id, that.id)
                    && Objects.equals(this.embedding, that.embedding)
                    && Objects.equals(this.chunkFilePath, that.chunkFilePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, embedding, chunkFilePath);
        }
    }

    static class HybridStoreData {
        @JsonProperty("entries")
        List<Entry<TextSegment>> entries;

        @JsonProperty("chunkStorageDirectory")
        String chunkStorageDirectory;

        @JsonProperty("cacheSize")
        int cacheSize;

        @JsonCreator
        HybridStoreData(
                @JsonProperty("entries") List<Entry<TextSegment>> entries,
                @JsonProperty("chunkStorageDirectory") String chunkStorageDirectory,
                @JsonProperty("cacheSize") int cacheSize) {
            this.entries = entries != null ? entries : new ArrayList<>();
            this.chunkStorageDirectory = chunkStorageDirectory;
            this.cacheSize = cacheSize;
        }

        // Constructor for generic entries - converts to TextSegment entries
        @SuppressWarnings("unchecked")
        HybridStoreData(Collection<? extends Entry<?>> genericEntries, String chunkStorageDirectory, int cacheSize) {
            this.entries = new ArrayList<>();
            for (Entry<?> entry : genericEntries) {
                this.entries.add((Entry<TextSegment>) entry);
            }
            this.chunkStorageDirectory = chunkStorageDirectory;
            this.cacheSize = cacheSize;
        }
    }

    // Helper class for TextSegment serialization
    static class ChunkData {
        @JsonProperty("text")
        public String text;

        @JsonProperty("metadata")
        public Map<String, Object> metadata;

        @JsonCreator
        public ChunkData(@JsonProperty("text") String text, @JsonProperty("metadata") Map<String, Object> metadata) {
            this.text = text;
            this.metadata = metadata;
        }
    }
}
