package dev.langchain4j.store.embedding.cassandra;

import com.datastax.astra.sdk.AstraClient;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.dtsx.astra.sdk.cassio.MetadataVectorCassandraTable;
import com.dtsx.astra.sdk.cassio.SimilarityMetric;
import com.dtsx.astra.sdk.cassio.SimilaritySearchQuery;
import com.dtsx.astra.sdk.cassio.SimilaritySearchResult;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import lombok.NonNull;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static java.util.stream.Collectors.toList;

/**
 * Implementation of {@link EmbeddingStore} using Cassandra.
 *
 * @see EmbeddingStore
 * @see MetadataVectorCassandraTable
 */
public class CassandraEmbeddingStore implements EmbeddingStore<TextSegment> {

    /**
     * Represents an embedding table in Cassandra, it is a table with a vector column.
     */
    protected MetadataVectorCassandraTable embeddingTable;

    /**
     * Cassandra question.
     */
    protected CqlSession cassandraSession;

    /**
     * Embedding Store.
     *
     * @param session
     *      cassandra Session
     * @param tableName
     *      table name
     * @param dimension
     *      dimension
     */
    public CassandraEmbeddingStore(CqlSession session, String tableName, int dimension) {
        this(session, tableName, dimension, SimilarityMetric.COS);
    }

    /**
     * Embedding Store.
     *
     * @param session
     *      cassandra Session
     * @param tableName
     *      table name
     * @param dimension
     *      dimension
     * @param metric
     *      metric
     */
    public CassandraEmbeddingStore(CqlSession session, String tableName, int dimension, SimilarityMetric metric) {
        this.cassandraSession = session;
        this.embeddingTable = new MetadataVectorCassandraTable(session, session.getKeyspace().get().asInternal(), tableName, dimension, metric);
    }

    /**
     * Create the table if not exist.
     */
    public void create() {
        embeddingTable.create();
    }

    /**
     * Delete the table.
     */
    public void delete() {
        embeddingTable.delete();
    }

    /**
     * Delete all rows.
     */
    public void clear() {
        embeddingTable.clear();
    }

    public static class Builder {
        public static Integer DEFAULT_PORT = 9042;
        private List<String> contactPoints;
        private String localDataCenter;
        private Integer port = DEFAULT_PORT;
        private String userName;
        private String password;
        protected String keyspace;
        protected String table;
        protected Integer dimension;
        protected SimilarityMetric metric = SimilarityMetric.COS;

        public Builder contactPoints(List<String> contactPoints) {
            this.contactPoints = contactPoints;
            return this;
        }

        public Builder localDataCenter(String localDataCenter) {
            this.localDataCenter = localDataCenter;
            return this;
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder keyspace(String keyspace) {
            this.keyspace = keyspace;
            return this;
        }

        public Builder table(String table) {
            this.table = table;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder metric(SimilarityMetric metric) {
            this.metric = metric;
            return this;
        }

        public Builder() {
        }

        public CassandraEmbeddingStore build() {
            CqlSessionBuilder builder = CqlSession.builder()
                    .withKeyspace(keyspace)
                    .withLocalDatacenter(localDataCenter);
            if (userName != null && password != null) {
                builder.withAuthCredentials(userName, password);
            }
            contactPoints.forEach(cp -> builder.addContactPoint(new InetSocketAddress(cp, port)));
            return new CassandraEmbeddingStore(builder.build(),table, dimension, metric);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BuilderAstra builderAstra() {
        return new BuilderAstra();
    }

    public static class BuilderAstra {
        private String token;
        private String dbId;
        private String tableName;
        private int dimension;
        private String keyspaceName = "default_keyspace";
        private String dbRegion = "us-east1";
        private SimilarityMetric metric = SimilarityMetric.COS;

        public BuilderAstra token(String token) {
            this.token = token;
            return this;
        }

        public BuilderAstra databaseId(String dbId) {
            this.dbId = dbId;
            return this;
        }

        public BuilderAstra databaseRegion(String dbRegion) {
            this.dbRegion = dbRegion;
            return this;
        }

        public BuilderAstra keyspace(String keyspaceName) {
            this.keyspaceName = keyspaceName;
            return this;
        }

        public BuilderAstra table(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public BuilderAstra dimension(int dimension) {
            this.dimension = dimension;
            return this;
        }

        public BuilderAstra metric(SimilarityMetric metric) {
            this.metric = metric;
            return this;
        }

        public CassandraEmbeddingStore build() {
            return new CassandraEmbeddingStore(AstraClient.builder()
                    .withToken(token)
                    .withCqlKeyspace(keyspaceName)
                    .withDatabaseId(dbId)
                    .withDatabaseRegion(dbRegion)
                    .enableCql()
                    .enableDownloadSecureConnectBundle()
                    .build().cqlSession(), tableName, dimension, metric);
        }
    }

    /**
     * Gets cassandraSession
     *
     * @return value of cassandraSession
     */
    public CqlSession getCassandraSession() {
        return cassandraSession;
    }

    /**
     * Add a new embedding to the store.
     * - the row id is generated
     * - text and metadata are not stored
     *
     * @param embedding representation of the list of floats
     * @return newly created row id
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
     * @param embedding   representation of the list of floats
     * @param textSegment text content and metadata
     * @return newly created row id
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
     * @param rowId     the row id
     * @param embedding representation of the list of floats
     */
    @Override
    public void add(@NonNull String rowId, @NonNull Embedding embedding) {
        embeddingTable.put(new MetadataVectorCassandraTable.Record(rowId, embedding.vectorAsList()));
    }

    /**
     * Add multiple embeddings as a single action.
     *
     * @param embeddingList embeddings list
     * @return list of new row if (same order as the input)
     */
    @Override
    public List<String> addAll(List<Embedding> embeddingList) {
        return embeddingList.stream()
                .map(Embedding::vectorAsList)
                .map(MetadataVectorCassandraTable.Record::new)
                .peek(embeddingTable::putAsync)
                .map(MetadataVectorCassandraTable.Record::getRowId)
                .collect(toList());
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

    /**
     * Search for relevant.
     *
     * @param embedding  current embeddings
     * @param maxResults max number of result
     * @param minScore   threshold
     * @return list of matching elements
     */
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding embedding, int maxResults, double minScore) {
        return embeddingTable
                .similaritySearch(SimilaritySearchQuery.builder()
                        .embeddings(embedding.vectorAsList())
                        .recordCount(ensureGreaterThanZero(maxResults, "maxResults"))
                        .threshold(CosineSimilarity.fromRelevanceScore(ensureBetween(minScore, 0, 1, "minScore")))
                        .distance(SimilarityMetric.COS)
                        .build())
                .stream()
                .map(CassandraEmbeddingStore::mapSearchResult)
                .collect(toList());
    }

    /**
     * Map Search result coming from Astra.
     *
     * @param record current record
     * @return search result
     */
    private static EmbeddingMatch<TextSegment> mapSearchResult(SimilaritySearchResult<MetadataVectorCassandraTable.Record> record) {
        return new EmbeddingMatch<>(
                // Score
                RelevanceScore.fromCosineSimilarity(record.getSimilarity()),
                // EmbeddingId : unique identifier
                record.getEmbedded().getRowId(),
                // Embeddings vector
                Embedding.from(record.getEmbedded().getVector()),
                // Text segment and metadata
                TextSegment.from(record.getEmbedded().getBody(), new Metadata(record.getEmbedded().getMetadata())));
    }

    /**
     * Similarity Search ANN based on the embedding.
     *
     * @param embedding  vector
     * @param maxResults max number of results
     * @param minScore   score minScore
     * @param metadata   map key-value to build a metadata filter
     * @return list of matching results
     */
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding embedding, int maxResults, double minScore, Metadata metadata) {
        SimilaritySearchQuery.SimilaritySearchQueryBuilder builder = SimilaritySearchQuery.builder()
                .embeddings(embedding.vectorAsList())
                .recordCount(ensureGreaterThanZero(maxResults, "maxResults"))
                .threshold(CosineSimilarity.fromRelevanceScore(ensureBetween(minScore, 0, 1, "minScore")));
        if (metadata != null) {
            builder.metaData(metadata.asMap());
        }
        return embeddingTable
                .similaritySearch(builder.build())
                .stream()
                .map(CassandraEmbeddingStore::mapSearchResult)
                .collect(toList());
    }
}
