package dev.langchain4j.store.embedding.inmemory;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

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
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A hybrid {@link dev.langchain4j.store.embedding.EmbeddingStore} that stores embeddings in memory and embedded content
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
public class HybridEmbeddingStore<Embedded> extends InMemoryEmbeddingStore<Embedded> {

    private static final Logger log = LoggerFactory.getLogger(HybridEmbeddingStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    // JSON serialization keys
    private static final String PARENT_DATA_KEY = "parentData";
    private static final String HYBRID_DATA_KEY = "hybridData";

    private final Path chunkStorageDirectory;
    private final Map<String, Embedded> chunkCache;
    private final int cacheSize;
    private final Map<String, String> entryToFileMapping; // Maps entry ID to chunk file path

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
        super(); // Call parent constructor
        this.chunkStorageDirectory = ensureNotNull(chunkStorageDirectory, "chunkStorageDirectory");
        this.cacheSize = Math.max(0, cacheSize);
        this.chunkCache = cacheSize > 0 ? createLRUCache(cacheSize) : new ConcurrentHashMap<>();
        this.entryToFileMapping = new ConcurrentHashMap<>();
        createChunkStorageDirectory();
        log.debug(
                "Created HybridEmbeddingStore with storage directory: {} and cache size: {}",
                chunkStorageDirectory,
                cacheSize);
    }

    private HybridEmbeddingStore(Path chunkStorageDirectory, int cacheSize, Map<String, String> entryToFileMapping) {
        super();
        this.chunkStorageDirectory = ensureNotNull(chunkStorageDirectory, "chunkStorageDirectory");
        this.cacheSize = Math.max(0, cacheSize);
        this.chunkCache = cacheSize > 0 ? createLRUCache(cacheSize) : new ConcurrentHashMap<>();
        this.entryToFileMapping = new ConcurrentHashMap<>(entryToFileMapping);
        createChunkStorageDirectory();
    }

    @Override
    public void add(String id, Embedding embedding, Embedded embedded) {
        String chunkFilePath = null;
        if (embedded != null) {
            chunkFilePath = saveChunkToFile(id, embedded);
            entryToFileMapping.put(id, chunkFilePath);
        }

        // Add to parent class with null embedded since we store it as a file
        super.add(id, embedding, null);
        log.debug("Added embedding with id: {} and chunk file: {}", id, chunkFilePath);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<Embedded> embedded) {
        if (ids.size() != embeddings.size() || embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("The list of ids and embeddings and embedded must have the same size");
        }

        List<String> processedIds = new ArrayList<>(ids.size());
        List<Embedding> processedEmbeddings = new ArrayList<>(embeddings.size());
        List<Embedded> processedEmbedded = new ArrayList<>(embedded.size());

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            Embedded embeddedContent = embedded.get(i);

            String chunkFilePath = null;
            if (embeddedContent != null) {
                chunkFilePath = saveChunkToFile(id, embeddedContent);
                entryToFileMapping.put(id, chunkFilePath);
            }

            processedIds.add(id);
            processedEmbeddings.add(embedding);
            processedEmbedded.add(null); // Store null since we use files
        }

        // Add to parent class
        super.addAll(processedIds, processedEmbeddings, processedEmbedded);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        // Clean up files and cache before removing from parent
        for (String id : ids) {
            String chunkFilePath = entryToFileMapping.remove(id);
            if (chunkFilePath != null) {
                deleteChunkFile(chunkFilePath);
            }
            chunkCache.remove(id);
        }

        // Remove from parent class
        super.removeAll(ids);
        log.debug("Removed {} embeddings", ids.size());
    }

    @Override
    public void removeAll(Filter filter) {
        // Find IDs to remove by checking the filter against entries
        List<String> idsToRemove = new ArrayList<>();

        // Since entries in hybrid store have null embedded content, we need to load from files to check filter
        for (Entry<Embedded> entry : entries) {
            if (filter != null) {
                // Load the embedded content from file to check against filter
                String chunkFilePath = entryToFileMapping.get(entry.id);
                if (chunkFilePath != null) {
                    Embedded embedded = loadChunkFromFile(chunkFilePath);
                    if (embedded instanceof TextSegment) {
                        Metadata metadata = ((TextSegment) embedded).metadata();
                        if (filter.test(metadata)) {
                            idsToRemove.add(entry.id);
                        }
                    }
                }
            } else {
                // If no filter, remove all
                idsToRemove.add(entry.id);
            }
        }

        // Clean up chunk files for identified entries
        for (String id : idsToRemove) {
            String chunkFilePath = entryToFileMapping.remove(id);
            if (chunkFilePath != null) {
                deleteChunkFile(chunkFilePath);
            }
            chunkCache.remove(id);
        }

        // Remove from parent class using IDs only if there are IDs to remove
        if (!idsToRemove.isEmpty()) {
            super.removeAll(idsToRemove);
        }
        log.debug("Removed {} embeddings matching filter", idsToRemove.size());
    }

    @Override
    public void removeAll() {
        // Clean up all files and cache
        for (String chunkFilePath : entryToFileMapping.values()) {
            deleteChunkFile(chunkFilePath);
        }
        entryToFileMapping.clear();
        chunkCache.clear();

        // Remove from parent class
        super.removeAll();
        log.debug("Removed all embeddings");
    }

    @Override
    public EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest embeddingSearchRequest) {
        // We need to handle filtering ourselves since parent entries have null embedded content
        Filter filter = embeddingSearchRequest.filter();
        List<EmbeddingMatch<Embedded>> matches = new ArrayList<>();

        for (Entry<Embedded> entry : entries) {
            boolean includeEntry = true;

            // Apply filter if present
            if (filter != null) {
                String chunkFilePath = entryToFileMapping.get(entry.id);
                if (chunkFilePath != null) {
                    Embedded embedded = loadChunkFromFile(chunkFilePath);
                    if (embedded instanceof TextSegment) {
                        Metadata metadata = ((TextSegment) embedded).metadata();
                        includeEntry = filter.test(metadata);
                    } else {
                        includeEntry = false; // Skip non-TextSegment entries when filter is present
                    }
                } else {
                    includeEntry = false; // Skip entries without chunk files when filter is present
                }
            }

            if (includeEntry) {
                // Calculate similarity score
                double cosineSimilarity =
                        CosineSimilarity.between(entry.embedding, embeddingSearchRequest.queryEmbedding());
                double score = RelevanceScore.fromCosineSimilarity(cosineSimilarity);

                if (score >= embeddingSearchRequest.minScore()) {
                    // Load embedded content for this match
                    String chunkFilePath = entryToFileMapping.get(entry.id);
                    Embedded embedded = null;
                    if (chunkFilePath != null) {
                        embedded = loadChunkFromFile(chunkFilePath);
                    }

                    matches.add(new EmbeddingMatch<>(score, entry.id, entry.embedding, embedded));
                }
            }
        }

        // Sort by score (highest first) and limit results
        matches.sort((a, b) -> Double.compare(b.score(), a.score()));
        if (matches.size() > embeddingSearchRequest.maxResults()) {
            matches = matches.subList(0, embeddingSearchRequest.maxResults());
        }

        log.debug("Found {} matches for search request", matches.size());
        return new EmbeddingSearchResult<>(matches);
    }

    /**
     * Configures the chunk storage base directory.
     *
     * @param chunkStorageDirectory New directory for storing chunk files
     * @return A new HybridEmbeddingStore instance with the specified directory
     */
    public HybridEmbeddingStore<Embedded> withChunkStorageDirectory(Path chunkStorageDirectory) {
        // Create a new HybridEmbeddingStore with the new directory
        HybridEmbeddingStore<Embedded> newStore = new HybridEmbeddingStore<>(chunkStorageDirectory, cacheSize);

        // Now we can directly copy the entries since we're in the same package!
        // This is much more efficient than serialization or search
        newStore.entries.addAll(this.entries);

        // Copy the file mappings (they reference the same files, so just copy the mapping)
        newStore.entryToFileMapping.putAll(this.entryToFileMapping);

        return newStore;
    }

    /**
     * Serializes the store to JSON, including the in-memory index and chunk file paths.
     */
    public String serializeToJson() {
        try {
            // Create a temporary InMemoryEmbeddingStore to get clean parent serialization
            InMemoryEmbeddingStore<Embedded> tempParentStore = new InMemoryEmbeddingStore<>();

            // Copy only the entries that have embedded content (loaded from files when needed)
            for (Entry<Embedded> entry : this.entries) {
                if (entry.embedded != null) {
                    tempParentStore.entries.add(entry);
                } else {
                    // For entries without embedded content, we need to load it from file to serialize properly
                    String fileName = entryToFileMapping.get(entry.id);
                    if (fileName != null) {
                        Embedded embedded = loadChunkFromFile(fileName);
                        Entry<Embedded> entryWithContent = new Entry<>(entry.id, entry.embedding, embedded);
                        tempParentStore.entries.add(entryWithContent);
                    } else {
                        // Entry without file mapping - add as-is (this might be an in-memory-only entry)
                        tempParentStore.entries.add(entry);
                    }
                }
            }

            // Get parent's clean serialization as string
            String parentJson = tempParentStore.serializeToJson();

            // Create hybrid-specific data
            HybridStoreData hybridData =
                    new HybridStoreData(entryToFileMapping, chunkStorageDirectory.toString(), cacheSize);

            // Create combined data structure
            Map<String, Object> combinedData = new LinkedHashMap<>();
            combinedData.put(PARENT_DATA_KEY, parentJson);
            combinedData.put(HYBRID_DATA_KEY, hybridData);

            return OBJECT_MAPPER.writeValueAsString(combinedData);
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
    @SuppressWarnings("unchecked")
    public static HybridEmbeddingStore<TextSegment> fromJson(String json) {
        try {
            // Parse the combined data structure
            Map<String, Object> combinedData = OBJECT_MAPPER.readValue(json, Map.class);

            // Extract parent data and hybrid data
            String parentJson = (String) combinedData.get(PARENT_DATA_KEY);
            Map<String, Object> hybridDataMap = (Map<String, Object>) combinedData.get(HYBRID_DATA_KEY);

            // Deserialize hybrid data
            HybridStoreData hybridData = OBJECT_MAPPER.convertValue(hybridDataMap, HybridStoreData.class);

            // Create the hybrid store with the hybrid-specific data
            HybridEmbeddingStore<TextSegment> hybridStore = new HybridEmbeddingStore<>(
                    Paths.get(hybridData.chunkStorageDirectory), hybridData.cacheSize, hybridData.entryToFileMapping);

            // Load the parent data using the parent's deserialization
            InMemoryEmbeddingStore<TextSegment> parentStore = InMemoryEmbeddingStore.fromJson(parentJson);

            // Now we can directly copy the entries since we're in the same package!
            hybridStore.entries.addAll(parentStore.entries);

            return hybridStore;
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

    static class HybridStoreData {
        @JsonProperty("entryToFileMapping")
        Map<String, String> entryToFileMapping;

        @JsonProperty("chunkStorageDirectory")
        String chunkStorageDirectory;

        @JsonProperty("cacheSize")
        int cacheSize;

        @JsonCreator
        HybridStoreData(
                @JsonProperty("entryToFileMapping") Map<String, String> entryToFileMapping,
                @JsonProperty("chunkStorageDirectory") String chunkStorageDirectory,
                @JsonProperty("cacheSize") int cacheSize) {
            this.entryToFileMapping = entryToFileMapping != null ? entryToFileMapping : new LinkedHashMap<>();
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
