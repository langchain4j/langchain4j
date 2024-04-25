package dev.langchain4j.store.embedding.astradb;

import com.datastax.astra.client.Collection;
import com.datastax.astra.client.model.DataAPIKeywords;
import com.datastax.astra.client.model.Document;
import com.datastax.astra.client.model.Filter;
import com.datastax.astra.client.model.InsertManyOptions;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.datastax.astra.client.model.Filters.eq;
import static com.datastax.astra.client.model.FindOneAndReplaceOptions.Builder.upsert;
import static com.datastax.astra.client.model.FindOptions.Builder.sort;

/**
 * Implementation of {@link EmbeddingStore} using AstraDB.
 *
 * @see EmbeddingStore
 */
@Slf4j
@Getter @Setter
@Accessors(fluent = true)
public class AstraDBEmbeddingStore implements EmbeddingStore<TextSegment> {

   /**
    * Saving the text chunk as an attribute.
    */
   public static final String KEY_ATTRIBUTES_BLOB = "content";

    /**
     * Metadata used for similarity.
     */
    public static final String KEY_SIMILARITY = "$similarity";

    /**
     * Client to work with an Astra Collection
     */
    private final Collection<Document> astraDBCollection;

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
    public AstraDBEmbeddingStore(@NonNull Collection<Document>  client) {
        this(client, 20, 8);
    }

    /**
     * Initialization of the store with an EXISTING collection.
     *
     * @param client
     *      astra db collection client
     * @param itemsPerChunk
     *     size of 1 chunk in between 1 and 20
     * @param concurrentThreads
     *      concurrent threads
     */
    public AstraDBEmbeddingStore(@NonNull Collection<Document> client, int itemsPerChunk, int concurrentThreads) {
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
                .insertOne(fromEmbeddingToDocument(embedding, textSegment))
                .getInsertedId().toString();
    }

    /** {@inheritDoc}  */
    @Override
    public void add(String id, Embedding embedding) {
        astraDBCollection.findOneAndReplace(eq(id),
                new Document(id).vector(embedding.vector()), upsert(true));
    }

    /** {@inheritDoc}  */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        if (embeddings == null) return null;

        // Map as a JsonDocument list.
        List<Document> recordList = embeddings
                .stream()
                .map(e -> fromEmbeddingToDocument(e, null))
                .collect(Collectors.toList());

        // Ids are Generated
        InsertManyOptions options = InsertManyOptions.Builder
                .chunkSize(itemsPerChunk)
                .concurrency(concurrentThreads)
                .ordered(false);
        return astraDBCollection.insertMany(recordList, options)
                .getInsertedIds().stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    /**
     * Add multiple embeddings as a single action.
     *
     * @param embeddingList
     *      list of embeddings
     * @param textSegmentList
     *      list of text segment
     *
     * @return list of new row if (same order as the input)
     */
    public List<String> addAll(List<Embedding> embeddingList, List<TextSegment> textSegmentList) {
        if (embeddingList == null || textSegmentList == null || embeddingList.size() != textSegmentList.size()) {
            throw new IllegalArgumentException("embeddingList and textSegmentList must not be null and have the same size");
        }

        // Map Documents list
        List<Document> recordList = IntStream.range(0, embeddingList.size())
                .mapToObj(i -> fromEmbeddingToDocument(embeddingList.get(i), textSegmentList.get(i)))
                .collect(Collectors.toList());

        // Set options for distributed treatment
        InsertManyOptions options = InsertManyOptions.Builder
                .chunkSize(itemsPerChunk)
                .concurrency(concurrentThreads)
                .ordered(false);

        // Insert Many
        return astraDBCollection.insertMany(recordList, options)
                .getInsertedIds().stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    /** {@inheritDoc}  */
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        return findRelevant(referenceEmbedding, (Filter) null, maxResults, minScore);
    }

    /**
     * Implementation of the Search to add the metadata Filtering.
     *
     * @param request
     *      A request to search in an {@link EmbeddingStore}. Contains all search criteria.
     * @return
     *      search with metadata filtering
     */
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        // Mapping of the filter to internal representation
        Filter astraFilter = null;
        if (request.filter() != null) {
            astraFilter = AstraDBFilterMapper.map(request.filter());
        }
        // Call the search
        List<EmbeddingMatch<TextSegment>> matches = findRelevant(
                request.queryEmbedding(), astraFilter,
                request.maxResults(),
                request.minScore());
        // Build the result
        return new EmbeddingSearchResult<>(matches);
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
        return astraDBCollection
                .find(metaDatafilter, sort(referenceEmbedding.vector())
                        .limit(maxResults)
                        .includeSimilarity())
                .all().stream()
                .filter(r -> r.getSimilarity().isPresent() &&  r.getSimilarity().get()>= minScore)
                .map(this::fromDocumentToEmbeddingMatch)
                .collect(Collectors.toList());
    }

    /**
     * Mapping the output of the query to a {@link EmbeddingMatch}..
     *
     * @param doc
     *      returned object as Json
     * @return
     *      embedding match as expected by langchain4j
     */
    private EmbeddingMatch<TextSegment> fromDocumentToEmbeddingMatch(Document doc) {
        Double score        = doc.getSimilarity().orElse(0d);
        String embeddingId  = doc.getId(String.class);
        Embedding embedding = Embedding.from(doc.getVector().orElse(null));
        TextSegment embedded = null;
        Object body = doc.get(KEY_ATTRIBUTES_BLOB);
        if (body != null) {
            Metadata metadata = new Metadata(doc.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                entry -> entry.getValue() == null ? "" : entry.getValue().toString()
            )));
            metadata.remove(KEY_ATTRIBUTES_BLOB);
            metadata.remove(DataAPIKeywords.ID.getKeyword());
            metadata.remove(DataAPIKeywords.VECTOR.getKeyword());
            metadata.remove(DataAPIKeywords.VECTORIZE.getKeyword());
            metadata.remove(DataAPIKeywords.SIMILARITY.getKeyword());
            embedded = new TextSegment(body.toString(), metadata);
        }
        return new EmbeddingMatch<>(score, embeddingId, embedding, embedded);
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
    private Document fromEmbeddingToDocument(Embedding embedding, TextSegment textSegment) {
        Document record = new Document().vector(embedding.vector());
        if (textSegment != null) {
            record.append(KEY_ATTRIBUTES_BLOB, textSegment.text())
                  .putAll(textSegment.metadata().asMap());
        }
        return record;
    }

}
