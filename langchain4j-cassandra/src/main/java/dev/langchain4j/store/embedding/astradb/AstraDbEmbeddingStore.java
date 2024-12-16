package dev.langchain4j.store.embedding.astradb;

import com.dtsx.astra.sdk.AstraDBCollection;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.stargate.sdk.data.domain.JsonDocument;
import io.stargate.sdk.data.domain.JsonDocumentMutationResult;
import io.stargate.sdk.data.domain.JsonDocumentResult;
import io.stargate.sdk.data.domain.odm.Document;
import io.stargate.sdk.data.domain.query.Filter;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.randomUUID;

/**
 * Implementation of {@link EmbeddingStore} using AstraDB.
 *
 * @see EmbeddingStore
 */
@Slf4j
@Getter @Setter
@Accessors(fluent = true)
public class AstraDbEmbeddingStore implements EmbeddingStore<TextSegment> {

   /**
    * Saving the text chunk as an attribut.
    */
   public static final String KEY_ATTRIBUTES_BLOB = "body_blob";

    /**
     * Metadata used for similarity.
     */
    public static final String KEY_SIMILARITY = "$similarity";

    /**
     * Client to work with an Astra Collection
     */
    private final AstraDBCollection astraDBCollection;

    /**
     * Bulk loading are processed in chunks, size of 1 chunk in between 1 and 20
     */
    private final int itemsPerChunk;

    /**
     * Bulk loading is distributed,the is the number threads
     */
    private final int concurrentThreads;

    /**
     * Initialization of the store with an EXISTING collection.
     *
     * @param client
     *      astra db collection client
     */
    public AstraDbEmbeddingStore(@NonNull AstraDBCollection client) {
        this(client, 20, 8);
    }

    /**
     * Initialization of the store with an EXISTING collection.
     *
     * @param client
     *      astra db collection client
     * @param itemsPerChunk
     *     size of 1 chunk in between 1 and 20
     */
    public AstraDbEmbeddingStore(@NonNull AstraDBCollection client, int itemsPerChunk, int concurrentThreads) {
        if (itemsPerChunk>20 || itemsPerChunk<1) {
            throw new IllegalArgumentException("'itemsPerChunk' should be in between 1 and 20");
        }
        if (concurrentThreads<1) {
            throw new IllegalArgumentException("'concurrentThreads' should be at least 1");
        }
        this.astraDBCollection = client;
        this.itemsPerChunk     = itemsPerChunk;
        this.concurrentThreads = concurrentThreads;
    }

    /**
     * Delete all records from the table.
     */
    public void clear() {
        astraDBCollection.deleteAll();
    }

    /** {@inheritDoc}  */
    @Override
    public String add(Embedding embedding) {
        return add(embedding, null);
    }

    /** {@inheritDoc}  */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        return astraDBCollection
                .insertOne(mapRecord(randomUUID(), embedding, textSegment))
                .getDocument().getId();
    }

    /** {@inheritDoc}  */
    @Override
    public void add(String id, Embedding embedding) {
        astraDBCollection.upsertOne(new JsonDocument().id(id).vector(embedding.vector()));
    }

    /** {@inheritDoc}  */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        if (embeddings == null) return null;

        // Map as a JsonDocument list.
        List<JsonDocument> recordList = embeddings
                .stream()
                .map(e -> mapRecord(randomUUID(), e, null))
                .collect(Collectors.toList());

        // No upsert needed as ids will be generated.
        return astraDBCollection
                .insertManyChunkedJsonDocuments(recordList, itemsPerChunk, concurrentThreads)
                .stream()
                .map(JsonDocumentMutationResult::getDocument)
                .map(Document::getId)
                .collect(Collectors.toList());
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddingList, List<TextSegment> textSegmentList) {
        if (embeddingList == null || textSegmentList == null || embeddingList.size() != textSegmentList.size()) {
            throw new IllegalArgumentException("embeddingList and textSegmentList must not be null and have the same size");
        }

        // Map as JsonDocument list
        List<JsonDocument> recordList = new ArrayList<>();
        for (int i = 0; i < embeddingList.size(); i++) {
            recordList.add(mapRecord(ids.get(i), embeddingList.get(i), textSegmentList.get(i)));
        }

        // No upsert needed (ids will be generated)
        astraDBCollection
                .insertManyChunkedJsonDocuments(recordList, itemsPerChunk, concurrentThreads);
    }

    /** {@inheritDoc}  */
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        return findRelevant(referenceEmbedding, (Filter) null, maxResults, minScore);
    }

    /**
     * Semantic search with metadata filtering.
     *
     * @param referenceEmbedding
     *      vector
     * @param metaDatafilter
     *      fileter for metadata
     * @param maxResults
     *      limit
     * @param minScore
     *      threshold
     * @return
     *      records
     */
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, Filter metaDatafilter, int maxResults, double minScore) {
        return astraDBCollection.findVector(referenceEmbedding.vector(), metaDatafilter,  maxResults)
                .filter(r -> r.getSimilarity() >= minScore)
                .map(this::mapJsonResult)
                .collect(Collectors.toList());
    }

    /**
     * Mapping the output of the query to a {@link EmbeddingMatch}..
     *
     * @param jsonRes
     *      returned object as Json
     * @return
     *      embedding match as expected by langchain4j
     */
    private EmbeddingMatch<TextSegment> mapJsonResult(JsonDocumentResult jsonRes) {
        Double score        = (double) jsonRes.getSimilarity();
        String embeddingId  = jsonRes.getId();
        Embedding embedding = Embedding.from(jsonRes.getVector());
        TextSegment embedded = null;
        Map<String, Object> properties = jsonRes.getData();
        if (properties!= null) {
            Object body = properties.get(KEY_ATTRIBUTES_BLOB);
            if (body != null) {
                Metadata metadata = new Metadata(properties.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                entry -> entry.getValue() == null ? "" : entry.getValue().toString()
                        )));
                metadata.remove(KEY_ATTRIBUTES_BLOB);
                metadata.remove(KEY_SIMILARITY);
                embedded = new TextSegment(body.toString(), metadata);
            }
        }
        return new EmbeddingMatch<TextSegment>(score, embeddingId, embedding, embedded);
    }

    /**
     * Map from LangChain4j record to AstraDB record.
     *
     * @param embedding
     *      embedding (vector)
     * @param textSegment
     *      text segment (text to encode)
     * @return
     *      a json document
     */
    private JsonDocument mapRecord(String id, Embedding embedding, TextSegment textSegment) {
        JsonDocument record = new JsonDocument().id(id).vector(embedding.vector());
        if (textSegment != null) {
            record.put(KEY_ATTRIBUTES_BLOB, textSegment.text());
            textSegment.metadata().asMap().forEach(record::put);
        }
        return record;
    }

}
