package dev.langchain4j.store.embedding.cassandra;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.CqlSession;
import com.dtsx.astra.sdk.cassio.MetadataVectorCassandraTable;
import com.dtsx.astra.sdk.cassio.SimilarityMetric;
import com.dtsx.astra.sdk.cassio.SimilaritySearchResult;
import com.dtsx.astra.sdk.cassio.SimilaritySearchQuery;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link EmbeddingStore} using Cassandra AstraDB.
 *
 * @see EmbeddingStore
 * @see MetadataVectorCassandraTable
 */
public class AstraDbEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

    /**
     * Represents an embedding table in Cassandra, it is a table with a vector column.
     */
    private final MetadataVectorCassandraTable embeddingTable;

    /**
     * Constructor building the CQL Session from the AstraClient.
     *
     * @param token        token
     * @param dbId         database identifier
     * @param dbRegion     database region
     * @param keyspaceName keyspace name
     * @param tableName    table name
     * @param dimension    vector dimension
     */
    public AstraDbEmbeddingStoreImpl(String token, String dbId, String dbRegion, String keyspaceName, String tableName, Integer dimension) {
        this(AstraClient.builder()
                .withToken(token)
                .withCqlKeyspace(keyspaceName)
                .withDatabaseId(dbId)
                .withDatabaseRegion(dbRegion)
                .enableCql()
                .enableDownloadSecureConnectBundle()
                .build().cqlSession(), keyspaceName, tableName, dimension);
    }

    /**
     * Constructor for message store
     *
     * @param session
     *      cassandra session
     * @param keyspaceName
     *      keyspace name
     * @param tableName
     *      table name
     * @param dimension
     *     vector dimension
     */
    public AstraDbEmbeddingStoreImpl(CqlSession session, String keyspaceName, String tableName, Integer dimension) {
        embeddingTable = new MetadataVectorCassandraTable(session, keyspaceName, tableName, dimension);
    }

    /**
     * Add a new embedding to the store.
     * - the row id is generated
     * - text and metadata are not stored
     *
     * @param embedding
     *      representation of the list of floats
     * @return
     *      newly created row id
     */
    @Override
    public String add(@NonNull Embedding embedding) {
        return add(embedding, null);
    }

    /**
     * Add a new embedding to the store.
     * - the row id is generated
     * - text and metadata coming from the text Segment
     *
     * @param embedding
     *      representation of the list of floats
     * @param textSegment
     *      text content and metadata
     * @return
     *      newly created row id
     */
    @Override
    public String add(@NonNull Embedding embedding, TextSegment textSegment) {
        MetadataVectorCassandraTable.Record record = new MetadataVectorCassandraTable.Record(embedding.vectorAsList());
        if (textSegment != null) {
            record.setBody(textSegment.text());
            record.setMetadata(textSegment.metadata().asMap());
        }
        embeddingTable.put(record);
        return record.getRowId();
    }

    /**
     * Add a new embedding to the store.
     *
     * @param rowId
     *      the row id
     * @param embedding
     *      representation of the list of floats
     */
    @Override
    public void add(@NonNull String rowId, @NonNull Embedding embedding) {
        embeddingTable.put(new MetadataVectorCassandraTable.Record(rowId, embedding.vectorAsList()));
    }

    /**
     * Add multiple embeddings as a single action.
     *
     * @param embeddinglist
     *      embeddings list
     * @return
     *      list of new row if (same order as the input)
     */
    @Override
    public List<String> addAll(List<Embedding> embeddinglist) {
        return embeddinglist.stream()
                .map(Embedding::vectorAsList)
                .map(MetadataVectorCassandraTable.Record::new)
                .peek(embeddingTable::putAsync)
                .map(MetadataVectorCassandraTable.Record::getRowId)
                .collect(Collectors.toList());
    }

    /**
     * Add multiple embeddings as a single action.
     *
     * @param embeddingList
     *      embeddings
     * @param textSegmentList
     *      text segments
     * @return
     *      list of new row if (same order as the input)
     */
    @Override
    public List<String> addAll(List<Embedding> embeddingList, List<TextSegment> textSegmentList) {
        if (embeddingList == null || textSegmentList == null || embeddingList.size() != textSegmentList.size()) {
            throw new IllegalArgumentException("embeddingList and textSegmentList must not be null and have the same size");
        }
        // Looping on both list with an index
        List<String> ids = new ArrayList<>();
        for(int i = 0; i < embeddingList.size(); i++) {
            ids.add(add(embeddingList.get(i), textSegmentList.get(i)));
        }
        return ids;
    }

    /**
     * Search for relevant.
     *
     * @param embedding
     *      current embeddings
     * @param maxResults
     *      max number of result
     * @param minScore
     *      threshold
     * @return
     *      list of matching elements
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding embedding, int maxResults, double minScore) {
        return embeddingTable
                .similaritySearch(SimilaritySearchQuery.builder()
                        .embeddings(embedding.vectorAsList())
                        .recordCount(maxResults)
                        .threshold(minScore)
                        .distance(SimilarityMetric.DOT_PRODUCT)
                        .build())
                .stream()
                .map(AstraDbEmbeddingStoreImpl::mapSearchResult)
                .collect(Collectors.toList());
    }

    /**
     * Map Search result coming from Astra.
     *
     * @param record
     *      current record
     * @return
     *      search result
     */
    private static EmbeddingMatch<TextSegment> mapSearchResult(SimilaritySearchResult<MetadataVectorCassandraTable.Record> record) {
        return new EmbeddingMatch<TextSegment>(
                // Score
                (double) record.getSimilarity(),
                // EmbeddingId : unique identifier
                record.getEmbedded().getRowId(),
                // Embeddings vector
                Embedding.from(record.getEmbedded().getVector()),
                // Text Fragment and metadata
                TextSegment.from(record.getEmbedded().getBody(), new Metadata(record.getEmbedded().getMetadata())));
    }

    /**
     * Similarity Search ANN based on the embedding.
     *
     * @param embedding
     *      vector
     * @param maxResults
     *      max number of record
     * @param threshold
     *      score threshold
     * @return
     *      list of matching results
     */
    public List<EmbeddingMatch<TextSegment>> similaritySearch(Embedding embedding, Integer maxResults, Double threshold, Metadata metadata) {
        SimilaritySearchQuery.SimilaritySearchQueryBuilder builder =
                SimilaritySearchQuery.builder().embeddings(embedding.vectorAsList());
        if (maxResults != null && maxResults > 0) {
            builder.recordCount(maxResults);
        }
        if (threshold!= null && threshold > 0d && threshold < 1d) {
            builder.threshold(threshold);
        }
        if (metadata != null) {
            builder.metaData(metadata.asMap());
        }
        return embeddingTable
                .similaritySearch(builder.build())
                .stream()
                .map(AstraDbEmbeddingStoreImpl::mapSearchResult)
                .collect(Collectors.toList());
    }

}
