package dev.langchain4j.store.embedding.astradb;

import com.dtsx.astra.sdk.AstraDB;
import com.dtsx.astra.sdk.cassio.MetadataVectorCassandraTable;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.stargate.sdk.json.CollectionClient;
import io.stargate.sdk.json.domain.CollectionDefinition;
import io.stargate.sdk.json.domain.Filter;
import io.stargate.sdk.json.domain.JsonDocument;
import io.stargate.sdk.json.domain.JsonResult;
import io.stargate.sdk.json.domain.SimilarityMetric;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of {@link EmbeddingStore} using Cassandra AstraDB.
 *
 * @see EmbeddingStore
 * @see MetadataVectorCassandraTable
 */
public class AstraDbEmbeddingStore implements EmbeddingStore<TextSegment> {
   /**
    * special property.
    */
   public static final String KEY_BODY = "body_blob";
   /**
    * special property.
   */
   public static final String KEY_ATTRIBUTES_BLOB = "body_blob";

    /**
     * Astra Db Client
     */
   private final AstraDB astradb;

    /**
     * Astra Collection
     */
    private CollectionClient collection;

    private final String collectionName;

    private final Integer dimension;

    private final SimilarityMetric metric;

    /**
     * Embedding Store.
     * @param token
     *      current token
     * @param apiEndpoint
     *      current api endpoint
     * @param collectionName
     *      collection name
     * @param dimension
     *      collection dimension
     */
    public AstraDbEmbeddingStore(String token, String apiEndpoint, String collectionName, Integer dimension) {
        this(token, apiEndpoint, collectionName, dimension, SimilarityMetric.cosine);
   }

    /**
     * Working with a Collection
     * @param token
     *      astra token
     * @param apiEndpoint
     *      astra endpoing
     * @param collectionName
     *      astra collection name
     */
    public AstraDbEmbeddingStore(String token, String apiEndpoint, String collectionName) {
        this(token, apiEndpoint, collectionName, null, null);
    }

    /**
     * Working with a Collection
     * @param token
     *      astra token
     * @param apiEndpoint
     *      astra endpoing
     * @param collectionName
     *      astra collection name
     * @param dimension
     *      dimension for the vector
     * @param metric
     *      similarity metric
     */
   public AstraDbEmbeddingStore(@NonNull String token, @NonNull String apiEndpoint, @NonNull String collectionName, @NonNull Integer dimension, @NonNull SimilarityMetric metric) {
        astradb = new AstraDB(token, apiEndpoint);
        this.collectionName = collectionName;
        this.dimension = dimension;
        this.metric = metric;
       Optional<CollectionDefinition> collectionDef = astradb.findCollection(collectionName);
       if (collectionDef.isPresent()) {
           if (dimension != collectionDef.get().getOptions().getVector().getDimension()) {
               throw new IllegalArgumentException("Invalid dimension for collection " + collectionName
                       + " expected " + collectionDef.get().getOptions().getVector().getDimension() + " got " + dimension);
           }
           if (!metric.equals(collectionDef.get().getOptions().getVector().getMetric())) {
               throw new IllegalArgumentException("Invalid metric for collection " + collectionName
                       + " expected " + collectionDef.get().getOptions().getVector().getMetric() + " got " + metric);
           }
           collection = astradb.collection(collectionName);
       }
    }

    /**
     * Create the table if not exist.
     */
    public void create() {
        if (collection == null) {
            collection = astradb.createCollection(CollectionDefinition
                    .builder().name(collectionName)
                    .vector(dimension, metric).build());
        }
    }

    /**
     * Delete the table.
     */
    public void delete() {
        astradb.deleteCollection(collectionName);
        collection = null;
    }

    /**
     * Delete all rows.
     */
    public void clear() {
        delete();
        create();
    }


    /** {@inheritDoc}  */
    @Override
    public String add(Embedding embedding) {
        return add(embedding, null);
    }

    /** {@inheritDoc}  */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        JsonDocument newVector = new JsonDocument().vector(embedding.vector());
        if (textSegment != null) {
            newVector.put(KEY_BODY, textSegment.text());
            textSegment.metadata().asMap().forEach(newVector::put);
        }
        return collection.insertOne(newVector);
    }

    /** {@inheritDoc}  */
    @Override
    public void add(String id, Embedding embedding) {
        collection.insertOne(new JsonDocument().id(id).vector(embedding.vector()));
    }

    /** {@inheritDoc}  */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        final int blockSize = 20;
        List<String> inserted = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i += blockSize) {
            inserted.addAll(collection.insertMany(embeddings.subList(i, Math.min(i + blockSize, embeddings.size()))
                    .stream()
                    .map(e -> new JsonDocument().vector(e.vector()))
                    .collect(Collectors.toList())));
        }
        return inserted;
    }

    /**
     * Add multiple embeddings as a single action.
     *
     * @param embeddingList   embeddings
     * @param textSegmentList text segments
     * @return list of new row if (same order as the input)
     */
    @Override
    public List<String> addAll(List<Embedding> embeddingList, List<TextSegment> textSegmentList) {
        if (embeddingList == null || textSegmentList == null || embeddingList.size() != textSegmentList.size()) {
            throw new IllegalArgumentException("embeddingList and textSegmentList must not be null and have the same size");
        }
        // Looping on both list with an index
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < embeddingList.size(); i++) {
            ids.add(add(embeddingList.get(i), textSegmentList.get(i)));
        }
        return ids;
    }

    /** {@inheritDoc}  */
    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        return findRelevant(referenceEmbedding, null, maxResults, minScore);
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
        return collection.similaritySearch(referenceEmbedding.vector(), metaDatafilter,  maxResults)
                .stream()
                .filter(r -> r.getSimilarity() >= minScore)
                .map(this::mapJsonResult)
                .collect(Collectors.toList());
    }

    private EmbeddingMatch<TextSegment> mapJsonResult(JsonResult jsonRes) {
        System.out.println(jsonRes.toString());
        Double score        = (double) jsonRes.getSimilarity();
        String embeddingId  = jsonRes.getId();
        Embedding embedding = Embedding.from(jsonRes.getVector());
        TextSegment embedded = null;
        Map<String, Object> properties = jsonRes.getData();
        if (properties!= null) {
            String body = properties.getOrDefault(KEY_BODY, "").toString();
            Metadata metadata = new Metadata(properties.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            entry -> entry.getValue() == null ? "" : entry.getValue().toString()
                    )));
            // Body is a reserved keyword
            metadata.remove(KEY_BODY);
            embedded = new TextSegment(body, metadata);

        }
        return new EmbeddingMatch<TextSegment>(score, embeddingId, embedding, embedded);
    }

    /**
     * Gets collection
     *
     * @return value of collection
     */
    public CollectionClient getCollectionClient() {
        return collection;
    }

    /**
     * Gets astradb
     *
     * @return value of astradb
     */
    public AstraDB getAstradb() {
        return astradb;
    }

    /**
     * Gets collectionName
     *
     * @return value of collectionName
     */
    public String getCollectionName() {
        return collectionName;
    }

    /**
     * Gets dimension
     *
     * @return value of dimension
     */
    public Integer getDimension() {
        return dimension;
    }

    /**
     * Gets metric
     *
     * @return value of metric
     */
    public SimilarityMetric getMetric() {
        return metric;
    }
}
