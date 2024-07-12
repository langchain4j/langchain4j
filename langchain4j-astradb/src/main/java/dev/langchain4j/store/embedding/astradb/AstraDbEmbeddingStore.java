package dev.langchain4j.store.embedding.astradb;

import com.datastax.astra.client.Collection;
import com.datastax.astra.client.model.DataAPIKeywords;
import com.datastax.astra.client.model.Document;
import com.datastax.astra.client.model.Filter;
import com.datastax.astra.client.model.Filters;
import com.datastax.astra.client.model.FindOneAndReplaceOptions;
import com.datastax.astra.client.model.FindOptions;
import com.datastax.astra.client.model.InsertManyOptions;
import com.datastax.astra.client.model.Projections;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
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
import static com.datastax.astra.client.model.Filters.in;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.singletonList;

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
     * @param collection
     *      astra db collection client
     */
    public AstraDbEmbeddingStore(@NonNull Collection<Document> collection) {
        this(collection, 20, 8);
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
    public AstraDbEmbeddingStore(@NonNull Collection<Document> client, int itemsPerChunk, int concurrentThreads) {
        if (itemsPerChunk>100 || itemsPerChunk<1) {
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
                new Document(id).vector(embedding.vector()),
                new FindOneAndReplaceOptions().upsert(true));
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
        InsertManyOptions options = new InsertManyOptions()
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
        if (textSegmentList == null) {
            throw new IllegalArgumentException("textSegmentList must not be null");
        }

        // Map Documents list
        List<Document> recordList = IntStream.range(0, textSegmentList.size())
                .mapToObj(i -> fromEmbeddingToDocument((embeddingList == null) ? null : embeddingList.get(i), textSegmentList.get(i)))
                .collect(Collectors.toList());

        // Set options for distributed treatment
        InsertManyOptions options = new InsertManyOptions()
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
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequestAstra request) {
        // Mapping of the filter to internal representation
        Filter astraFilter = null;
        if (request.filter() != null) {
            astraFilter = AstraDbFilterMapper.map(request.filter());
        }
        if (request.query() != null) {
            return new EmbeddingSearchResult<>(findRelevant(request.query(),
                    astraFilter, request.maxResults(), request.minScore()));
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
                .find(metaDatafilter, new FindOptions()
                        .sort(referenceEmbedding.vector())
                        .limit(maxResults)
                        .projection(Projections.include("*"))
                        .includeSimilarity())
                .all().stream()
                .filter(r -> r.getSimilarity().isPresent() &&  r.getSimilarity().get()>= minScore)
                .map(this::fromDocumentToEmbeddingMatch)
                .collect(Collectors.toList());
    }

    /**
     * Removes a single embedding from the store by ID.
     *
     * @param id The unique ID of the embedding to be removed.
     */
    @Override
    public void remove(String id) {
        astraDBCollection.deleteOne(eq(id));
    }

    /**
     * Removes all embeddings that match the specified IDs from the store.
     *
     * @param ids A collection of unique IDs of the embeddings to be removed.
     */
    @Override
    public void removeAll(java.util.Collection<String> ids) {
        astraDBCollection.deleteMany(in(DataAPIKeywords.ID.getKeyword(), ids));
    }

    /**
     * Removes all embeddings that match the specified {@link dev.langchain4j.store.embedding.filter.Filter} from the store.
     *
     * @param filter The filter to be applied to the {@link Metadata} of the {@link TextSegment} during removal.
     *               Only embeddings whose {@code TextSegment}'s {@code Metadata}
     *               match the {@code Filter} will be removed.
     */
    @Override
    public void removeAll(dev.langchain4j.store.embedding.filter.Filter filter) {
        astraDBCollection.deleteMany(AstraDbFilterMapper.map(filter));
    }

    /**
     * Semantic search with metadata filtering.
     *
     * @param vectorize
     *      string that will be encoded on site in the DB
     * @param metaDatafilter
     *      fileter for metadata
     * @param maxResults
     *      limit
     * @param minScore
     *      threshold
     * @return
     *      records
     */
    protected List<EmbeddingMatch<TextSegment>> findRelevant(String vectorize, Filter metaDatafilter, int maxResults, double minScore) {
        return astraDBCollection
                .find(metaDatafilter, new FindOptions()
                        .sort(vectorize)
                        .limit(maxResults)
                        .projection(Projections.include("*"))
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
        Embedding embedding = null;
        if (doc.getVector().isPresent()) {
            embedding = Embedding.from(doc.getVector().get());
        }
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
        Document record = new Document();
        if (embedding!=null) {
            record.vector(embedding.vector());
        } else {
            record.vectorize(textSegment.text());
        }
        if (textSegment != null) {
            record.append(KEY_ATTRIBUTES_BLOB, textSegment.text())
                  .putAll(textSegment.metadata().toMap());
        }
        return record;
    }

}
