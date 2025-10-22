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
import static dev.langchain4j.store.embedding.pgvector.FullTextIndexType.GIN;
import static java.lang.String.join;
import static java.util.Collections.nCopies;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import javax.sql.DataSource;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.pgvector.PGvector;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PGVector EmbeddingStore Implementation
 * <p>
 * Only cosine similarity is used.
 * Only ivfflat index is used.
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
     * Text search configuration
     */
    private final String regconfig;
    /**
     * Should use Gin on text column for full-text search
     */
    private final FullTextIndexType fullTextIndexType;

    /**
     * Constructor for PgVectorEmbeddingStore Class
     *
     * @param datasource            The datasource to use
     * @param table                 The database table
     * @param dimension             The vector dimension
     * @param useIndex              Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param fullTextIndexType     full text index type, support <a href="https://www.postgresql.org/docs/current/gin-intro.html">GIN</a>...
     * @param regconfig             The text search configuration <a href="https://www.postgresql.org/docs/9.4/functions-textsearch.html">Text Search Functions and Operators</a>
     * @param indexListSize         The IVFFlat number of lists
     * @param createTable           Should create table automatically
     * @param dropTableFirst        Should drop table first, usually for testing
     * @param metadataStorageConfig The {@link MetadataStorageConfig} config.
     */
    protected PgVectorEmbeddingStore(DataSource datasource,
                                     String table,
                                     Integer dimension,
                                     Boolean useIndex,
                                     FullTextIndexType fullTextIndexType,
                                     String regconfig,
                                     Integer indexListSize,
                                     Boolean createTable,
                                     Boolean dropTableFirst,
                                     MetadataStorageConfig metadataStorageConfig) {
        this.datasource = ensureNotNull(datasource, "datasource");
        this.table = ensureNotBlank(table, "table");
        MetadataStorageConfig config = getOrDefault(metadataStorageConfig, DefaultMetadataStorageConfig.defaultConfig());
        this.metadataHandler = MetadataHandlerFactory.get(config);
        useIndex = getOrDefault(useIndex, false);
        this.fullTextIndexType = getOrDefault(fullTextIndexType, FullTextIndexType.NO_INDEX);
        this.regconfig = getOrDefault(regconfig, "english");
        createTable = getOrDefault(createTable, true);
        dropTableFirst = getOrDefault(dropTableFirst, false);

        initTable(dropTableFirst, createTable, useIndex, dimension, indexListSize);
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
     * @param fullTextIndexType     full text index type, support <a href="https://www.postgresql.org/docs/current/gin-intro.html">GIN</a>...
     * @param regconfig             The text search configuration <a href="https://www.postgresql.org/docs/9.4/functions-textsearch.html">Text Search Functions and Operators</a>
     * @param indexListSize         The IVFFlat number of lists
     * @param createTable           Should create table automatically
     * @param dropTableFirst        Should drop table first, usually for testing
     * @param metadataStorageConfig The {@link MetadataStorageConfig} config.
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
            FullTextIndexType fullTextIndexType,
            String regconfig,
            Integer indexListSize,
            Boolean createTable,
            Boolean dropTableFirst,
            MetadataStorageConfig metadataStorageConfig
    ) {
        this(createDataSource(host, port, user, password, database),
                table, dimension, useIndex, fullTextIndexType, regconfig, indexListSize, createTable, dropTableFirst, metadataStorageConfig);
    }

    public PgVectorEmbeddingStore() {
        this.datasource = null;
        this.table = null;
        this.metadataHandler = null;
        this.regconfig = null;
        this.fullTextIndexType = null;
    }

    private static DataSource createDataSource(String host, Integer port, String user, String password, String database) {
        host = ensureNotBlank(host, "host");
        port = ensureGreaterThanZero(port, "port");
        user = ensureNotBlank(user, "user");
        password = ensureNotBlank(password, "password");
        database = ensureNotBlank(database, "database");

        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerNames(new String[]{host});
        source.setPortNumbers(new int[]{port});
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
    protected void initTable(Boolean dropTableFirst, Boolean createTable, Boolean useIndex, Integer dimension,
                             Integer indexListSize) {
        String query = "init";
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            if (dropTableFirst) {
                statement.executeUpdate(String.format("DROP TABLE IF EXISTS %s", table));
            }
            if (createTable) {
                query = buildCreateTableSQL(dimension);
                statement.executeUpdate(query);
                metadataHandler.createMetadataIndexes(statement, table);
            }
            if (useIndex) {
                final String indexName = table + "_ivfflat_index";
                query = String.format(
                        "CREATE INDEX IF NOT EXISTS %s ON %s " +
                                "USING ivfflat (embedding vector_cosine_ops) " +
                                "WITH (lists = %s)",
                        indexName, table, ensureGreaterThanZero(indexListSize, "indexListSize"));
                statement.executeUpdate(query);
            }
            if (fullTextIndexType == GIN) {
                query = String.format("CREATE INDEX IF NOT EXISTS %s_text_search_idx ON %s USING GIN (text_tsv)",
                        table, table);
                statement.executeUpdate(query);
            }
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Failed to execute '%s'", query), e);
        }
    }

    private String buildCreateTableSQL(Integer dimension) {
        if (fullTextIndexType == GIN) { // need a tetevtor column to build index
            return String.format(
                    "CREATE TABLE IF NOT EXISTS %s (embedding_id UUID PRIMARY KEY, " +
                            "embedding vector(%s), text TEXT NULL, %s , " +
                            "text_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('%s', text)) STORED)",
                    table, ensureGreaterThanZero(dimension, "dimension"),
                    metadataHandler.columnDefinitionsString(), regconfig);
        }
        return String.format("CREATE TABLE IF NOT EXISTS %s (embedding_id UUID PRIMARY KEY, " +
                        "embedding vector(%s), text TEXT NULL, %s)",
                table, ensureGreaterThanZero(dimension, "dimension"),
                metadataHandler.columnDefinitionsString());
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
            Array array = connection.createArrayOf("uuid", ids.stream().map(UUID::fromString).toArray());
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

        String referenceVector = Arrays.toString(referenceEmbedding.vector());
        String whereClause = (filter == null) ? "" : metadataHandler.whereClause(filter);
        whereClause = (whereClause.isEmpty()) ? "" : "WHERE " + whereClause;
        String query =
                "WITH " + embeddingCTESQL(referenceVector, whereClause, false) +
                        String.format("SELECT * FROM embedding_result\n\tWHERE score >= %s ORDER BY score desc LIMIT %s;", minScore, maxResults);

        return searchInternal(query, i -> {
        });
    }

    @Experimental
    public EmbeddingSearchResult<TextSegment> fullTextSearch(String content, Filter filter, int maxResults, double minScore) {
        String filterClause = (filter == null) ? "" : metadataHandler.whereClause(filter);
        String query =
                "WITH " + fullTextCTESQL(filterClause, false) +
                        "SELECT * FROM full_text_result\n\tWHERE score >= ? ORDER BY score desc LIMIT ?;";

        return searchInternal(query, preparedStatement -> {
            try {
                preparedStatement.setString(1, content);
                preparedStatement.setString(2, content);
                preparedStatement.setDouble(3, minScore);
                preparedStatement.setInt(4, maxResults);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Experimental
    public EmbeddingSearchResult<TextSegment> hybridSearch(
            Embedding embedding, String content,
            Filter filter, int maxResults, double minScore, int rrfK) {

        String referenceVector = Arrays.toString(embedding.vector());
        String filterCondition = (filter == null) ? "" : metadataHandler.whereClause(filter);
        String metadataColumns = metadataHandler.columnsNames().stream().map(
                column -> String.format("coalesce(full_text_result.%s, embedding_result.%s) as %s ", column, column, column)
        ).collect(Collectors.joining(","));
        String rrf = "coalesce(1.0 / (? + full_text_result.score), 0.0) + coalesce(1.0 / (? + embedding_result.score), 0.0)";
        String queryTemplate = "WITH \n%s,\n%s\n" +
                "SELECT %s as score, coalesce(full_text_result.embedding_id, embedding_result.embedding_id) as embedding_id, " +
                "coalesce(full_text_result.embedding, embedding_result.embedding) as embedding, coalesce(full_text_result.text, embedding_result.text) as text, " +
                metadataColumns +
                "\n\tFROM full_text_result FULL OUTER JOIN embedding_result USING (embedding_id)\n\tWHERE %s >= ? ORDER BY score desc LIMIT ?;";
        String query = String.format(
                queryTemplate,
                fullTextCTESQL(filterCondition, true), embeddingCTESQL(referenceVector, filterCondition, true),
                rrf, rrf);
        return searchInternal(query, preparedStatement -> {
            try {
                preparedStatement.setString(1, content);
                preparedStatement.setString(2, content);
                preparedStatement.setInt(3, rrfK);
                preparedStatement.setInt(4, rrfK);
                preparedStatement.setInt(5, rrfK);
                preparedStatement.setInt(6, rrfK);
                preparedStatement.setDouble(7, minScore);
                preparedStatement.setInt(8, maxResults);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }


    private String embeddingCTESQL(String referenceVector, String filterCondition, Boolean rankAsScore) {
        String scoreColumn = rankAsScore ? "row_number() over (order by embedding <=> '%s')" :
                "(2 - (embedding <=> '%s')) / 2";
        return String.format(
                "embedding_result AS (\n\tSELECT " + scoreColumn + " AS score, embedding_id, embedding, text, %s FROM %s %s\n)\n",
                referenceVector, join(",", metadataHandler.columnsNames()), table, filterCondition);
    }

    private String fullTextCTESQL(String filterCondition, Boolean rankAsScore) {
        // 32 is normalization factor to make the score between 0 and 1. The score is calculated by rank / (rank + 1).
        String scoreColumn = rankAsScore ? "row_number() over (order by ts_rank_cd(%s, %s, 32) desc)" :
                "ts_rank_cd(%s, %s, 32)";
        String toQuery = String.format("plainto_tsquery('%s', ?)", regconfig);
        String fullTextColumn = fullTextIndexType == GIN ? "text_tsv" : String.format("to_tsvector('%s', text)", regconfig);
        return String.format(
                "full_text_result AS (\n\tSELECT " + scoreColumn + " AS score, embedding_id, embedding, text, %s FROM %s " +
                        "\n\t\tWHERE %s @@ %s %s\n)\n",
                fullTextColumn, toQuery,
                join(",", metadataHandler.columnsNames()), table,
                fullTextColumn, toQuery, filterCondition);
    }

    EmbeddingSearchResult<TextSegment> searchInternal(String query, Consumer<PreparedStatement> preparedStatementSetter) {
        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement selectStmt = connection.prepareStatement(query)) {
            preparedStatementSetter.accept(selectStmt);
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
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return new EmbeddingSearchResult<>(result);
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(
                singletonList(id),
                singletonList(embedding),
                embedded == null ? null : singletonList(embedded));
    }

    @Override
    public void addAll(
            List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        try (Connection connection = getConnection()) {
            String query = String.format(
                    "INSERT INTO %s (embedding_id, embedding, text, %s) VALUES (?, ?, ?, %s)" +
                            "ON CONFLICT (embedding_id) DO UPDATE SET " +
                            "embedding = EXCLUDED.embedding," +
                            "text = EXCLUDED.text," +
                            "%s;",
                    table, join(",", metadataHandler.columnsNames()),
                    join(",", nCopies(metadataHandler.columnsNames().size(), "?")),
                    metadataHandler.insertClause());
            try (PreparedStatement upsertStmt = connection.prepareStatement(query)) {
                for (int i = 0; i < ids.size(); ++i) {
                    upsertStmt.setObject(1, UUID.fromString(ids.get(i)));
                    upsertStmt.setObject(2, new PGvector(embeddings.get(i).vector()));

                    if (embedded != null && embedded.get(i) != null) {
                        upsertStmt.setObject(3, embedded.get(i).text());
                        metadataHandler.setMetadata(upsertStmt, 4, embedded.get(i).metadata());
                    } else {
                        upsertStmt.setNull(3, Types.VARCHAR);
                        IntStream.range(4, 4 + metadataHandler.columnsNames().size()).forEach(
                                j -> {
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
        private String regconfig;
        /**
         * Should use Gin on text column for full-text search
         */
        private FullTextIndexType fullTextIndexType;

        DatasourceBuilder() {
        }

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

        public PgVectorEmbeddingStore build() {
            return new PgVectorEmbeddingStore(this.datasource, this.table, this.dimension, this.useIndex, this.fullTextIndexType, this.regconfig, this.indexListSize, this.createTable, this.dropTableFirst, this.metadataStorageConfig);
        }

        public String toString() {
            return "PgVectorEmbeddingStore.DatasourceBuilder(datasource=" + this.datasource + ", table=" + this.table + ", dimension=" + this.dimension + ", useIndex=" + this.useIndex + ", indexListSize=" + this.indexListSize + ", createTable=" + this.createTable + ", dropTableFirst=" + this.dropTableFirst + ", metadataStorageConfig=" + this.metadataStorageConfig + ")";
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
        private String regconfig;
        /**
         * Should use Gin on text column for full-text search
         */
        private FullTextIndexType fullTextIndexType;

        PgVectorEmbeddingStoreBuilder() {
        }

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

        public PgVectorEmbeddingStoreBuilder fullTextIndexType(FullTextIndexType fullTextIndexType) {
            this.fullTextIndexType = fullTextIndexType;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder regconfig(String regconfig) {
            this.regconfig = regconfig;
            return this;
        }

        public PgVectorEmbeddingStore build() {
            return new PgVectorEmbeddingStore(this.host, this.port, this.user, this.password, this.database, this.table, this.dimension, this.useIndex, this.fullTextIndexType, this.regconfig, this.indexListSize, this.createTable, this.dropTableFirst, this.metadataStorageConfig);
        }

        public String toString() {
            return "PgVectorEmbeddingStore.PgVectorEmbeddingStoreBuilder(host=" + this.host + ", port=" + this.port + ", user=" + this.user + ", password=" + this.password + ", database=" + this.database + ", table=" + this.table + ", dimension=" + this.dimension + ", useIndex=" + this.useIndex + ", indexListSize=" + this.indexListSize + ", createTable=" + this.createTable + ", dropTableFirst=" + this.dropTableFirst + ", metadataStorageConfig=" + this.metadataStorageConfig + ")";
        }
    }

}
