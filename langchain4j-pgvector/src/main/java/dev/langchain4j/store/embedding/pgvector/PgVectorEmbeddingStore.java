package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.lang.String.join;
import static java.util.Collections.nCopies;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.pgvector.PGvector;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PGVector EmbeddingStore Implementation
 * <p>
 * Supports three query types:
 * <ul>
 *   <li>{@link PgVectorQueryType#VECTOR}: Uses cosine similarity for semantic search (default)</li>
 *   <li>{@link PgVectorQueryType#FULL_TEXT}: Uses PostgreSQL full-text search</li>
 *   <li>{@link PgVectorQueryType#HYBRID}: Combines vector and full-text search with configurable weights</li>
 * </ul>
 * <p>
 * IVFFlat index is used for vector similarity search.
 * GIN index is used for full-text search when HYBRID or FULL_TEXT query type is configured.
 */
// Needed for inherited bean injection validation
public class PgVectorEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final Logger log = LoggerFactory.getLogger(PgVectorEmbeddingStore.class);
    /**
     * Datasource used to create the store
     */
    protected final DataSource datasource;
    /**
     * Embeddings table name
     */
    protected final String table;
    /**
     * Metadata handler
     */
    final MetadataHandler metadataHandler;
    /**
     * Query type for search operations
     */
    protected final PgVectorQueryType queryType;
    /**
     * Weight for vector similarity score in hybrid search (default 0.6)
     */
    protected final Double vectorWeight;
    /**
     * Weight for full-text search score in hybrid search (default 0.4)
     */
    protected final Double textWeight;
    /**
     * PostgreSQL full-text search language configuration (default 'english')
     */
    protected final String ftsLanguage;

    /**
     * Constructor for PgVectorEmbeddingStore Class
     *
     * @param datasource            The datasource to use
     * @param table                 The database table
     * @param dimension             The vector dimension
     * @param useIndex              Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param indexListSize         The IVFFlat number of lists
     * @param createTable           Should create table automatically
     * @param dropTableFirst        Should drop table first, usually for testing
     * @param metadataStorageConfig The {@link MetadataStorageConfig} config.
     * @param queryType             The query type for search operations (VECTOR, FULL_TEXT, or HYBRID)
     * @param vectorWeight          Weight for vector similarity in hybrid search (default 0.6)
     * @param textWeight            Weight for full-text search in hybrid search (default 0.4)
     * @param ftsLanguage           PostgreSQL full-text search language configuration (default 'english')
     */
    protected PgVectorEmbeddingStore(
            DataSource datasource,
            String table,
            Integer dimension,
            Boolean useIndex,
            Integer indexListSize,
            Boolean createTable,
            Boolean dropTableFirst,
            MetadataStorageConfig metadataStorageConfig,
            PgVectorQueryType queryType,
            Double vectorWeight,
            Double textWeight,
            String ftsLanguage) {
        this.datasource = ensureNotNull(datasource, "datasource");
        this.table = ensureNotBlank(table, "table");
        MetadataStorageConfig config =
                getOrDefault(metadataStorageConfig, DefaultMetadataStorageConfig.defaultConfig());
        this.metadataHandler = MetadataHandlerFactory.get(config);
        this.queryType = getOrDefault(queryType, PgVectorQueryType.VECTOR);
        this.vectorWeight = getOrDefault(vectorWeight, 0.6);
        this.textWeight = getOrDefault(textWeight, 0.4);
        this.ftsLanguage = getOrDefault(ftsLanguage, "english");
        useIndex = getOrDefault(useIndex, false);
        createTable = getOrDefault(createTable, true);
        dropTableFirst = getOrDefault(dropTableFirst, false);

        if (useIndex || createTable || dropTableFirst) {
            initTable(dropTableFirst, createTable, useIndex, dimension, indexListSize);
        }
    }

    /**
     * Constructor for PgVectorEmbeddingStore Class
     * Use this builder when you don't have datasource management.
     *
     * @param host                  The database host
     * @param port                  The database port
     * @param user                  The database user
     * @param password              The database password
     * @param database              The database name
     * @param table                 The database table
     * @param dimension             The vector dimension
     * @param useIndex              Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param indexListSize         The IVFFlat number of lists
     * @param createTable           Should create table automatically
     * @param dropTableFirst        Should drop table first, usually for testing
     * @param metadataStorageConfig The {@link MetadataStorageConfig} config.
     * @param queryType             The query type for search operations (VECTOR, FULL_TEXT, or HYBRID)
     * @param vectorWeight          Weight for vector similarity in hybrid search (default 0.6)
     * @param textWeight            Weight for full-text search in hybrid search (default 0.4)
     * @param ftsLanguage           PostgreSQL full-text search language configuration (default 'english')
     */
    @SuppressWarnings("unused")
    protected PgVectorEmbeddingStore(
            String host,
            Integer port,
            String user,
            String password,
            String database,
            String table,
            Integer dimension,
            Boolean useIndex,
            Integer indexListSize,
            Boolean createTable,
            Boolean dropTableFirst,
            MetadataStorageConfig metadataStorageConfig,
            PgVectorQueryType queryType,
            Double vectorWeight,
            Double textWeight,
            String ftsLanguage) {
        this(
                createDataSource(host, port, user, password, database),
                table,
                dimension,
                useIndex,
                indexListSize,
                createTable,
                dropTableFirst,
                metadataStorageConfig,
                queryType,
                vectorWeight,
                textWeight,
                ftsLanguage);
    }

    public PgVectorEmbeddingStore() {
        this.datasource = null;
        this.table = null;
        this.metadataHandler = null;
        this.queryType = PgVectorQueryType.VECTOR;
        this.vectorWeight = 0.6;
        this.textWeight = 0.4;
        this.ftsLanguage = "english";
    }

    private static DataSource createDataSource(
            String host, Integer port, String user, String password, String database) {
        host = ensureNotBlank(host, "host");
        port = ensureGreaterThanZero(port, "port");
        user = ensureNotBlank(user, "user");
        password = ensureNotBlank(password, "password");
        database = ensureNotBlank(database, "database");

        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerNames(new String[] {host});
        source.setPortNumbers(new int[] {port});
        source.setDatabaseName(database);
        source.setUser(user);
        source.setPassword(password);

        return source;
    }

    public static DatasourceBuilder datasourceBuilder() {
        return new DatasourceBuilder();
    }

    public static PgVectorEmbeddingStoreBuilder builder() {
        return new PgVectorEmbeddingStoreBuilder();
    }

    /**
     * Initialize metadata table following configuration
     *
     * @param dropTableFirst Should drop table first, usually for testing
     * @param createTable    Should create table automatically
     * @param useIndex       Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param dimension      The vector dimension
     * @param indexListSize  The IVFFlat number of lists
     */
    protected void initTable(
            Boolean dropTableFirst, Boolean createTable, Boolean useIndex, Integer dimension, Integer indexListSize) {
        String query = "init";
        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {
            if (dropTableFirst) {
                statement.executeUpdate(String.format("DROP TABLE IF EXISTS %s", table));
            }
            if (createTable) {
                // Include text_search column for full-text search if using HYBRID or FULL_TEXT query type
                String ftsColumnDef = "";
                if (queryType == PgVectorQueryType.HYBRID || queryType == PgVectorQueryType.FULL_TEXT) {
                    ftsColumnDef = String.format(
                            ", text_search tsvector GENERATED ALWAYS AS (to_tsvector('%s', COALESCE(text, ''))) STORED",
                            ftsLanguage);
                }
                query = String.format(
                        "CREATE TABLE IF NOT EXISTS %s (embedding_id UUID PRIMARY KEY, "
                                + "embedding vector(%s), text TEXT NULL, %s %s)",
                        table,
                        ensureGreaterThanZero(dimension, "dimension"),
                        metadataHandler.columnDefinitionsString(),
                        ftsColumnDef);
                statement.executeUpdate(query);
                metadataHandler.createMetadataIndexes(statement, table);

                // Create GIN index for full-text search if needed
                if (queryType == PgVectorQueryType.HYBRID || queryType == PgVectorQueryType.FULL_TEXT) {
                    final String ftsIndexName = table + "_fts_gin_index";
                    query = String.format(
                            "CREATE INDEX IF NOT EXISTS %s ON %s USING GIN (text_search)", ftsIndexName, table);
                    statement.executeUpdate(query);
                }
            }
            if (useIndex) {
                final String indexName = table + "_ivfflat_index";
                query = String.format(
                        "CREATE INDEX IF NOT EXISTS %s ON %s " + "USING ivfflat (embedding vector_cosine_ops) "
                                + "WITH (lists = %s)",
                        indexName, table, ensureGreaterThanZero(indexListSize, "indexListSize"));
                statement.executeUpdate(query);
            }
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Failed to execute '%s'", query), e);
        }
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param embedding The embedding to be added to the store.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        addInternal(id, embedding, null);
        return id;
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param id        The unique identifier for the embedding to be added.
     * @param embedding The embedding to be added to the store.
     */
    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    /**
     * Adds a given embedding and the corresponding content that has been embedded to the store.
     *
     * @param embedding   The embedding to be added to the store.
     * @param textSegment Original content that was embedded.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    /**
     * Adds multiple embeddings to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        String sql = String.format("DELETE FROM %s WHERE embedding_id = ANY (?)", table);
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            Array array = connection.createArrayOf(
                    "uuid", ids.stream().map(UUID::fromString).toArray());
            statement.setArray(1, array);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        String whereClause = metadataHandler.whereClause(filter);
        String sql = String.format("DELETE FROM %s WHERE %s", table, whereClause);
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll() {
        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(String.format("TRUNCATE TABLE %s", table));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Searches for the most similar (closest in the embedding space) {@link Embedding}s.
     * <br>
     * All search criteria are defined inside the {@link EmbeddingSearchRequest}.
     * <br>
     * {@link EmbeddingSearchRequest#filter()} is used to filter by meta dada.
     * <br>
     * The search behavior depends on the configured {@link PgVectorQueryType}:
     * <ul>
     *   <li>VECTOR: Uses cosine similarity for semantic search (default)</li>
     *   <li>FULL_TEXT: Uses PostgreSQL full-text search</li>
     *   <li>HYBRID: Combines vector and full-text search with configurable weights</li>
     * </ul>
     *
     * @param request A request to search in an {@link EmbeddingStore}. Contains all search criteria.
     * @return An {@link EmbeddingSearchResult} containing all found {@link Embedding}s.
     */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding referenceEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();
        String queryText = request.query();

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        try (Connection connection = getConnection()) {
            String referenceVector = Arrays.toString(referenceEmbedding.vector());
            String whereClause = (filter == null) ? "" : metadataHandler.whereClause(filter);
            whereClause = (whereClause.isEmpty()) ? "" : "AND " + whereClause;

            String query = buildSearchQuery(referenceVector, maxResults, minScore, whereClause, queryText);

            try (PreparedStatement selectStmt = connection.prepareStatement(query)) {
                try (ResultSet resultSet = selectStmt.executeQuery()) {
                    while (resultSet.next()) {
                        double score = resultSet.getDouble("score");
                        String embeddingId = resultSet.getString("embedding_id");

                        PGvector vector = (PGvector) resultSet.getObject("embedding");
                        Embedding embedding = new Embedding(vector.toArray());

                        String text = resultSet.getString("text");
                        TextSegment textSegment = null;
                        if (isNotNullOrBlank(text)) {
                            Metadata metadata = metadataHandler.fromResultSet(resultSet);
                            textSegment = TextSegment.from(text, metadata);
                        }
                        result.add(new EmbeddingMatch<>(score, embeddingId, embedding, textSegment));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return new EmbeddingSearchResult<>(result);
    }

    /**
     * Builds the search query based on the configured query type.
     *
     * @param referenceVector The query vector as a string
     * @param maxResults      Maximum number of results to return
     * @param minScore        Minimum score threshold
     * @param whereClause     Additional WHERE clause for filtering
     * @param queryText       The query text for full-text search (from EmbeddingSearchRequest.query())
     * @return The SQL query string
     */
    private String buildSearchQuery(
            String referenceVector, int maxResults, double minScore, String whereClause, String queryText) {
        String metadataColumns = join(",", metadataHandler.columnsNames());

        switch (queryType) {
            case FULL_TEXT:
                // Full-text search only - requires search text to be extracted from the embedding
                // Note: For full-text only, we still need a way to get the query text
                // This implementation uses vector search as fallback since we only have embedding
                return buildVectorSearchQuery(referenceVector, maxResults, minScore, whereClause, metadataColumns);

            case HYBRID:
                return buildHybridSearchQuery(
                        referenceVector, maxResults, minScore, whereClause, metadataColumns, queryText);

            case VECTOR:
            default:
                return buildVectorSearchQuery(referenceVector, maxResults, minScore, whereClause, metadataColumns);
        }
    }

    /**
     * Builds a vector-only search query using cosine similarity.
     */
    private String buildVectorSearchQuery(
            String referenceVector, int maxResults, double minScore, String whereClause, String metadataColumns) {
        return String.format(
                "SELECT (2 - (embedding <=> '%s')) / 2 AS score, embedding_id, embedding, text, %s FROM %s "
                        + "WHERE round(cast(float8 (embedding <=> '%s') as numeric), 8) <= round(2 - 2 * %s, 8) %s "
                        + "ORDER BY embedding <=> '%s' LIMIT %s;",
                referenceVector,
                metadataColumns,
                table,
                referenceVector,
                minScore,
                whereClause,
                referenceVector,
                maxResults);
    }

    /**
     * Builds a hybrid search query combining vector similarity and full-text search.
     * The final score is: (vectorWeight * vectorScore) + (textWeight * ftsScore)
     * <p>
     * For hybrid search, we retrieve more candidates using vector search and then
     * re-rank them using the combined score.
     *
     * @param queryText The query text from EmbeddingSearchRequest.query() for full-text search
     */
    private String buildHybridSearchQuery(
            String referenceVector,
            int maxResults,
            double minScore,
            String whereClause,
            String metadataColumns,
            String queryText) {
        // Hybrid search combines vector similarity with full-text search relevance
        // Vector score: (2 - cosine_distance) / 2 normalized to [0, 1]
        // FTS score: ts_rank normalized (we use ts_rank which returns values typically 0-1)

        // Escape query text to prevent SQL injection (escape single quotes by doubling them)
        String escapedQueryText = (queryText != null) ? queryText.replace("'", "''") : "";

        return String.format(
                "WITH vector_search AS ("
                        + "  SELECT embedding_id, embedding, text, %s, "
                        + "         (2 - (embedding <=> '%s')) / 2 AS vector_score "
                        + "  FROM %s "
                        + "  WHERE round(cast(float8 (embedding <=> '%s') as numeric), 8) <= round(2 - 2 * %s, 8) %s "
                        + ") "
                        + "SELECT "
                        + "  (%s * vector_score + %s * COALESCE(ts_rank(text_search, plainto_tsquery('%s', '%s')), 0)) AS score, "
                        + "  vs.embedding_id, vs.embedding, vs.text, %s "
                        + "FROM vector_search vs "
                        + "LEFT JOIN %s t ON vs.embedding_id = t.embedding_id "
                        + "ORDER BY score DESC "
                        + "LIMIT %s;",
                metadataColumns,
                referenceVector,
                table,
                referenceVector,
                minScore,
                whereClause,
                vectorWeight,
                textWeight,
                ftsLanguage,
                escapedQueryText,
                metadataColumns.isEmpty() ? "vs.*" : "vs." + metadataColumns.replace(",", ", vs."),
                table,
                maxResults);
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        try (Connection connection = getConnection()) {
            String query = String.format(
                    "INSERT INTO %s (embedding_id, embedding, text, %s) VALUES (?, ?, ?, %s)"
                            + "ON CONFLICT (embedding_id) DO UPDATE SET "
                            + "embedding = EXCLUDED.embedding,"
                            + "text = EXCLUDED.text,"
                            + "%s;",
                    table,
                    join(",", metadataHandler.columnsNames()),
                    join(",", nCopies(metadataHandler.columnsNames().size(), "?")),
                    metadataHandler.insertClause());
            try (PreparedStatement upsertStmt = connection.prepareStatement(query)) {
                for (int i = 0; i < ids.size(); ++i) {
                    upsertStmt.setObject(1, UUID.fromString(ids.get(i)));
                    upsertStmt.setObject(2, new PGvector(embeddings.get(i).vector()));

                    if (embedded != null && embedded.get(i) != null) {
                        upsertStmt.setObject(3, embedded.get(i).text());
                        metadataHandler.setMetadata(
                                upsertStmt, 4, embedded.get(i).metadata());
                    } else {
                        upsertStmt.setNull(3, Types.VARCHAR);
                        IntStream.range(4, 4 + metadataHandler.columnsNames().size())
                                .forEach(j -> {
                                    try {
                                        upsertStmt.setNull(j, Types.OTHER);
                                    } catch (SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }
                    upsertStmt.addBatch();
                }
                upsertStmt.executeBatch();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Datasource connection
     * Creates the vector extension and add the vector type if it does not exist.
     * Could be overridden in case extension creation and adding type is done at datasource initialization step.
     *
     * @return Datasource connection
     * @throws SQLException exception
     */
    protected Connection getConnection() throws SQLException {
        Connection connection = datasource.getConnection();
        // Find a way to do the following code in connection initialization.
        // Here we assume the datasource could handle a connection pool
        // and we should add the vector type on each connection
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
        }
        PGvector.addVectorType(connection);
        return connection;
    }

    public static class DatasourceBuilder {
        private DataSource datasource;
        private String table;
        private Integer dimension;
        private Boolean useIndex;
        private Integer indexListSize;
        private Boolean createTable;
        private Boolean dropTableFirst;
        private MetadataStorageConfig metadataStorageConfig;
        private PgVectorQueryType queryType;
        private Double vectorWeight;
        private Double textWeight;
        private String ftsLanguage;

        DatasourceBuilder() {}

        public DatasourceBuilder datasource(DataSource datasource) {
            this.datasource = datasource;
            return this;
        }

        public DatasourceBuilder table(String table) {
            this.table = table;
            return this;
        }

        public DatasourceBuilder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public DatasourceBuilder useIndex(Boolean useIndex) {
            this.useIndex = useIndex;
            return this;
        }

        public DatasourceBuilder indexListSize(Integer indexListSize) {
            this.indexListSize = indexListSize;
            return this;
        }

        public DatasourceBuilder createTable(Boolean createTable) {
            this.createTable = createTable;
            return this;
        }

        public DatasourceBuilder dropTableFirst(Boolean dropTableFirst) {
            this.dropTableFirst = dropTableFirst;
            return this;
        }

        public DatasourceBuilder metadataStorageConfig(MetadataStorageConfig metadataStorageConfig) {
            this.metadataStorageConfig = metadataStorageConfig;
            return this;
        }

        /**
         * Sets the query type for search operations.
         * @param queryType The query type (VECTOR, FULL_TEXT, or HYBRID)
         * @return this builder
         */
        public DatasourceBuilder queryType(PgVectorQueryType queryType) {
            this.queryType = queryType;
            return this;
        }

        /**
         * Sets the weight for vector similarity in hybrid search.
         * @param vectorWeight Weight for vector score (default 0.6)
         * @return this builder
         */
        public DatasourceBuilder vectorWeight(Double vectorWeight) {
            this.vectorWeight = vectorWeight;
            return this;
        }

        /**
         * Sets the weight for full-text search in hybrid search.
         * @param textWeight Weight for text search score (default 0.4)
         * @return this builder
         */
        public DatasourceBuilder textWeight(Double textWeight) {
            this.textWeight = textWeight;
            return this;
        }

        /**
         * Sets the PostgreSQL full-text search language configuration.
         * @param ftsLanguage Language configuration (default 'english')
         * @return this builder
         */
        public DatasourceBuilder ftsLanguage(String ftsLanguage) {
            this.ftsLanguage = ftsLanguage;
            return this;
        }

        public PgVectorEmbeddingStore build() {
            return new PgVectorEmbeddingStore(
                    this.datasource,
                    this.table,
                    this.dimension,
                    this.useIndex,
                    this.indexListSize,
                    this.createTable,
                    this.dropTableFirst,
                    this.metadataStorageConfig,
                    this.queryType,
                    this.vectorWeight,
                    this.textWeight,
                    this.ftsLanguage);
        }

        public String toString() {
            return "PgVectorEmbeddingStore.DatasourceBuilder(datasource=" + this.datasource + ", table=" + this.table
                    + ", dimension=" + this.dimension + ", useIndex=" + this.useIndex + ", indexListSize="
                    + this.indexListSize + ", createTable=" + this.createTable + ", dropTableFirst="
                    + this.dropTableFirst + ", metadataStorageConfig=" + this.metadataStorageConfig
                    + ", queryType=" + this.queryType + ", vectorWeight=" + this.vectorWeight
                    + ", textWeight=" + this.textWeight + ", ftsLanguage=" + this.ftsLanguage + ")";
        }
    }

    public static class PgVectorEmbeddingStoreBuilder {
        private String host;
        private Integer port;
        private String user;
        private String password;
        private String database;
        private String table;
        private Integer dimension;
        private Boolean useIndex;
        private Integer indexListSize;
        private Boolean createTable;
        private Boolean dropTableFirst;
        private MetadataStorageConfig metadataStorageConfig;
        private PgVectorQueryType queryType;
        private Double vectorWeight;
        private Double textWeight;
        private String ftsLanguage;

        PgVectorEmbeddingStoreBuilder() {}

        public PgVectorEmbeddingStoreBuilder host(String host) {
            this.host = host;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder port(Integer port) {
            this.port = port;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder user(String user) {
            this.user = user;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder password(String password) {
            this.password = password;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder database(String database) {
            this.database = database;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder table(String table) {
            this.table = table;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder useIndex(Boolean useIndex) {
            this.useIndex = useIndex;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder indexListSize(Integer indexListSize) {
            this.indexListSize = indexListSize;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder createTable(Boolean createTable) {
            this.createTable = createTable;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder dropTableFirst(Boolean dropTableFirst) {
            this.dropTableFirst = dropTableFirst;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder metadataStorageConfig(MetadataStorageConfig metadataStorageConfig) {
            this.metadataStorageConfig = metadataStorageConfig;
            return this;
        }

        /**
         * Sets the query type for search operations.
         * @param queryType The query type (VECTOR, FULL_TEXT, or HYBRID)
         * @return this builder
         */
        public PgVectorEmbeddingStoreBuilder queryType(PgVectorQueryType queryType) {
            this.queryType = queryType;
            return this;
        }

        /**
         * Sets the weight for vector similarity in hybrid search.
         * @param vectorWeight Weight for vector score (default 0.6)
         * @return this builder
         */
        public PgVectorEmbeddingStoreBuilder vectorWeight(Double vectorWeight) {
            this.vectorWeight = vectorWeight;
            return this;
        }

        /**
         * Sets the weight for full-text search in hybrid search.
         * @param textWeight Weight for text search score (default 0.4)
         * @return this builder
         */
        public PgVectorEmbeddingStoreBuilder textWeight(Double textWeight) {
            this.textWeight = textWeight;
            return this;
        }

        /**
         * Sets the PostgreSQL full-text search language configuration.
         * @param ftsLanguage Language configuration (default 'english')
         * @return this builder
         */
        public PgVectorEmbeddingStoreBuilder ftsLanguage(String ftsLanguage) {
            this.ftsLanguage = ftsLanguage;
            return this;
        }

        public PgVectorEmbeddingStore build() {
            return new PgVectorEmbeddingStore(
                    this.host,
                    this.port,
                    this.user,
                    this.password,
                    this.database,
                    this.table,
                    this.dimension,
                    this.useIndex,
                    this.indexListSize,
                    this.createTable,
                    this.dropTableFirst,
                    this.metadataStorageConfig,
                    this.queryType,
                    this.vectorWeight,
                    this.textWeight,
                    this.ftsLanguage);
        }

        public String toString() {
            return "PgVectorEmbeddingStore.PgVectorEmbeddingStoreBuilder(host=" + this.host + ", port=" + this.port
                    + ", user=" + this.user + ", password=" + this.password + ", database=" + this.database + ", table="
                    + this.table + ", dimension=" + this.dimension + ", useIndex=" + this.useIndex + ", indexListSize="
                    + this.indexListSize + ", createTable=" + this.createTable + ", dropTableFirst="
                    + this.dropTableFirst + ", metadataStorageConfig=" + this.metadataStorageConfig
                    + ", queryType=" + this.queryType + ", vectorWeight=" + this.vectorWeight
                    + ", textWeight=" + this.textWeight + ", ftsLanguage=" + this.ftsLanguage + ")";
        }
    }
}
