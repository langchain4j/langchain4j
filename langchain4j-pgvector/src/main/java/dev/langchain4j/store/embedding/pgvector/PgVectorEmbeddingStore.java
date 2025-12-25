package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
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
 * Only cosine similarity is used.
 * Only ivfflat index is used.
 */
// Needed for inherited bean injection validation
public class PgVectorEmbeddingStore implements EmbeddingStore<TextSegment> {

    /**
     * Search modes for the embedding store.
     */
    public enum SearchMode {
        EMBEDDING_ONLY,
        FULL_TEXT_ONLY,
        HYBRID
    }

    private static final Logger log = LoggerFactory.getLogger(PgVectorEmbeddingStore.class);

    private static final String DEFAULT_TEXT_SEARCH_CONFIG = "simple";
    private static final int DEFAULT_RRF_K = 60;

    private static final List<String> ALLOWED_TEXT_SEARCH_CONFIGS =
            List.of("simple", "english", "german", "french", "italian", "spanish", "portuguese", "dutch", "russian");

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
     * Search mode
     */
    private final SearchMode searchMode;

    private final String textSearchConfig;

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
     * @param searchMode            The search mode to use
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
            SearchMode searchMode,
            String textSearchConfig) {
        this.datasource = ensureNotNull(datasource, "datasource");
        this.table = ensureNotBlank(table, "table");
        MetadataStorageConfig config =
                getOrDefault(metadataStorageConfig, DefaultMetadataStorageConfig.defaultConfig());
        this.metadataHandler = MetadataHandlerFactory.get(config);
        useIndex = getOrDefault(useIndex, false);
        createTable = getOrDefault(createTable, true);
        dropTableFirst = getOrDefault(dropTableFirst, false);

        this.searchMode = getOrDefault(searchMode, SearchMode.EMBEDDING_ONLY);
        this.textSearchConfig = validateTextSearchConfig(getOrDefault(textSearchConfig, DEFAULT_TEXT_SEARCH_CONFIG));

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
     * @param indexListSize         The IVFFlat number of lists
     * @param createTable           Should create table automatically
     * @param dropTableFirst        Should drop table first, usually for testing
     * @param metadataStorageConfig The {@link MetadataStorageConfig} config.
     * @param searchMode            The search mode to use
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
            SearchMode searchMode,
            String textSearchConfig) {
        this(
                createDataSource(host, port, user, password, database),
                table,
                dimension,
                useIndex,
                indexListSize,
                createTable,
                dropTableFirst,
                metadataStorageConfig,
                searchMode,
                textSearchConfig);
    }

    public PgVectorEmbeddingStore() {
        this.datasource = null;
        this.table = null;
        this.metadataHandler = null;
        this.searchMode = null;
        this.textSearchConfig = DEFAULT_TEXT_SEARCH_CONFIG;
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
            if (createTable && (searchMode == SearchMode.FULL_TEXT_ONLY || searchMode == SearchMode.HYBRID)) {
                query = String.format(
                        "CREATE TABLE IF NOT EXISTS %s (embedding_id UUID PRIMARY KEY, "
                                + "embedding vector(%s), text TEXT NULL, %s )",
                        table,
                        ensureGreaterThanZero(dimension, "dimension"),
                        metadataHandler.columnDefinitionsString());
                statement.executeUpdate(query);
                metadataHandler.createMetadataIndexes(statement, table);

                String ftsIndexName = table + "_text_fts_gin_index";
                query = String.format(
                        "CREATE INDEX IF NOT EXISTS %s ON %s " + "USING gin (to_tsvector('%s', coalesce(text, '')))",
                        ftsIndexName, table, textSearchConfig);
                statement.executeUpdate(query);
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
     *
     * @param request A request to search in an {@link EmbeddingStore}. Contains all search criteria.
     * @return An {@link EmbeddingSearchResult} containing all found {@link Embedding}s.
     */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        SearchMode mode = getOrDefault(searchMode, SearchMode.EMBEDDING_ONLY);

        return switch (mode) {
            case EMBEDDING_ONLY -> embeddingOnlySearch(request);
            case FULL_TEXT_ONLY -> fullTextOnlySearch(request);
            case HYBRID -> hybridSearch(request);
        };
    }

    private EmbeddingSearchResult<TextSegment> embeddingOnlySearch(EmbeddingSearchRequest request) {
        Embedding referenceEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        try (Connection connection = getConnection()) {
            String referenceVector = Arrays.toString(referenceEmbedding.vector());
            String whereClause = (filter == null) ? "" : metadataHandler.whereClause(filter);
            whereClause = (whereClause.isEmpty()) ? "" : "AND " + whereClause;
            String query = String.format(
                    "SELECT (2 - (embedding <=> '%1$s')) / 2 AS score, " + "       embedding_id, "
                            + "       embedding, "
                            + "       text, "
                            + "       %2$s "
                            + "FROM %3$s "
                            + "WHERE round(cast(float8 (embedding <=> '%1$s') as numeric), 8) <= round(2 - 2 * %4$s, 8) "
                            + "      %5$s "
                            + "ORDER BY embedding <=> '%1$s' "
                            + "LIMIT %6$s;",
                    referenceVector,
                    join(",", metadataHandler.columnsNames()),
                    table,
                    minScore,
                    whereClause,
                    maxResults);
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

    private EmbeddingSearchResult<TextSegment> fullTextOnlySearch(EmbeddingSearchRequest request) {
        String keywordQuery = request.query();

        if (isNullOrBlank(keywordQuery)) {
            return new EmbeddingSearchResult<>(List.of());
        }

        int maxResults = request.maxResults();
        Filter filter = request.filter();

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();

        try (Connection connection = getConnection()) {
            String whereClause = (filter == null) ? "" : metadataHandler.whereClause(filter);
            whereClause = (whereClause.isEmpty()) ? "" : "AND " + whereClause;

            String sql = String.format(
                    "SELECT ts_rank(to_tsvector('%s', coalesce(text, '')), "
                            + "              plainto_tsquery('%s', ?)) AS score, "
                            + "       embedding_id, embedding, text, %s "
                            + "FROM %s "
                            + "WHERE to_tsvector('%s', coalesce(text, '')) "
                            + "      @@ plainto_tsquery('%s', ?) "
                            + "%s "
                            + "ORDER BY score DESC "
                            + "LIMIT ?;",
                    textSearchConfig,
                    textSearchConfig,
                    join(",", metadataHandler.columnsNames()),
                    table,
                    textSearchConfig,
                    textSearchConfig,
                    whereClause);

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, keywordQuery);
                stmt.setString(2, keywordQuery);
                stmt.setInt(3, maxResults);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        double score = rs.getDouble("score");
                        String embeddingId = rs.getString("embedding_id");

                        PGvector vector = (PGvector) rs.getObject("embedding");
                        Embedding embedding = new Embedding(vector.toArray());

                        String text = rs.getString("text");
                        TextSegment textSegment = null;
                        if (isNotNullOrBlank(text)) {
                            Metadata metadata = metadataHandler.fromResultSet(rs);
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

    private EmbeddingSearchResult<TextSegment> hybridSearch(EmbeddingSearchRequest request) {
        Embedding referenceEmbedding = request.queryEmbedding();
        String keywordQuery = request.query();

        if (isNullOrBlank(keywordQuery)) {
            return new EmbeddingSearchResult<>(List.of());
        }

        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();

        try (Connection connection = getConnection()) {
            String referenceVector = Arrays.toString(referenceEmbedding.vector());

            String filterCondition = (filter == null) ? "" : metadataHandler.whereClause(filter);
            String vectorWhere = filterCondition.isEmpty() ? "" : "WHERE " + filterCondition;
            String keywordWhere = filterCondition.isEmpty() ? "" : " AND " + filterCondition;

            List<String> metadataCols = metadataHandler.columnsNames();
            String rawMetadataCols = metadataCols.isEmpty() ? "" : ", " + String.join(", ", metadataCols);

            String coalescedMetadataCols = "";
            if (!metadataCols.isEmpty()) {
                coalescedMetadataCols = ", "
                        + metadataCols.stream()
                                .map(col -> String.format("COALESCE(v.%1$s, k.%1$s) AS %1$s", col))
                                .collect(java.util.stream.Collectors.joining(", "));
            }

            int rrfK = DEFAULT_RRF_K;

            String sql = String.format(
                    "WITH vector_search AS ( " + "  SELECT "
                            + "    embedding_id, embedding, text %1$s, "
                            + "    RANK() OVER (ORDER BY embedding <=> '%2$s') AS rnk "
                            + "  FROM %3$s "
                            + "  %4$s "
                            + "  ORDER BY embedding <=> '%2$s' "
                            + "  LIMIT %5$d "
                            + "), keyword_search AS ( "
                            + "  SELECT "
                            + "    embedding_id, embedding, text %1$s, "
                            + "    RANK() OVER (ORDER BY ts_rank(to_tsvector('%6$s', coalesce(text, '')), plainto_tsquery('%6$s', ?)) DESC) AS rnk "
                            + "  FROM %3$s "
                            + "  WHERE to_tsvector('%6$s', coalesce(text, '')) @@ plainto_tsquery('%6$s', ?) "
                            + "    %7$s "
                            + "  ORDER BY ts_rank(to_tsvector('%6$s', coalesce(text, '')), plainto_tsquery('%6$s', ?)) DESC "
                            + "  LIMIT %5$d "
                            + ") "
                            + "SELECT * FROM ( "
                            + "  SELECT "
                            + "    COALESCE(v.embedding_id, k.embedding_id) AS embedding_id, "
                            + "    COALESCE(v.embedding, k.embedding) AS embedding, "
                            + "    COALESCE(v.text, k.text) AS text "
                            + "    %8$s, "
                            + "    COALESCE(1.0 / (%9$d + v.rnk), 0.0) + COALESCE(1.0 / (%9$d + k.rnk), 0.0) AS score "
                            + "  FROM vector_search v "
                            + "  FULL OUTER JOIN keyword_search k ON v.embedding_id = k.embedding_id "
                            + ") ranked "
                            + "WHERE ranked.score >= ? "
                            + "ORDER BY ranked.score DESC "
                            + "LIMIT %10$d;",
                    rawMetadataCols,
                    referenceVector,
                    table,
                    vectorWhere,
                    Math.max(maxResults, rrfK),
                    textSearchConfig,
                    keywordWhere,
                    coalescedMetadataCols,
                    rrfK,
                    maxResults);

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, keywordQuery);
                stmt.setString(2, keywordQuery);
                stmt.setString(3, keywordQuery);
                stmt.setDouble(4, minScore);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        double score = rs.getDouble("score");
                        String embeddingId = rs.getString("embedding_id");

                        PGvector vector = (PGvector) rs.getObject("embedding");
                        Embedding embedding = new Embedding(vector.toArray());

                        String text = rs.getString("text");
                        TextSegment textSegment = null;
                        if (isNotNullOrBlank(text)) {
                            Metadata metadata = metadataHandler.fromResultSet(rs);
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

    private static String validateTextSearchConfig(String cfg) {
        if (cfg == null || cfg.isBlank()) {
            return DEFAULT_TEXT_SEARCH_CONFIG;
        }
        if (!ALLOWED_TEXT_SEARCH_CONFIGS.contains(cfg)) {
            throw new IllegalArgumentException(
                    "Unsupported textSearchConfig: '" + cfg + "'. Allowed values: " + ALLOWED_TEXT_SEARCH_CONFIGS);
        }
        return cfg;
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
        private SearchMode searchMode;
        private String textSearchConfig;

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

        public DatasourceBuilder searchMode(SearchMode searchMode) {
            this.searchMode = searchMode;
            return this;
        }

        public DatasourceBuilder textSearchConfig(String textSearchConfig) {
            this.textSearchConfig = textSearchConfig;
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
                    this.searchMode,
                    this.textSearchConfig);
        }

        public String toString() {
            return "PgVectorEmbeddingStore.DatasourceBuilder(datasource=" + this.datasource + ", table=" + this.table
                    + ", dimension=" + this.dimension + ", useIndex=" + this.useIndex + ", indexListSize="
                    + this.indexListSize + ", createTable=" + this.createTable + ", dropTableFirst="
                    + this.dropTableFirst + ", metadataStorageConfig=" + this.metadataStorageConfig + ", searchMode="
                    + this.searchMode + ", textSearchConfig=" + this.textSearchConfig + ")";
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
        private SearchMode searchMode;
        private String textSearchConfig;

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

        public PgVectorEmbeddingStoreBuilder searchMode(SearchMode searchMode) {
            this.searchMode = searchMode;
            return this;
        }

        public PgVectorEmbeddingStoreBuilder textSearchConfig(String textSearchConfig) {
            this.textSearchConfig = textSearchConfig;
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
                    this.searchMode,
                    this.textSearchConfig);
        }

        public String toString() {
            return "PgVectorEmbeddingStore.PgVectorEmbeddingStoreBuilder(host=" + this.host + ", port=" + this.port
                    + ", user=" + this.user + ", password=" + this.password + ", database=" + this.database + ", table="
                    + this.table + ", dimension=" + this.dimension + ", useIndex=" + this.useIndex + ", indexListSize="
                    + this.indexListSize + ", createTable=" + this.createTable + ", dropTableFirst="
                    + this.dropTableFirst + ", metadataStorageConfig=" + this.metadataStorageConfig + ")";
        }
    }
}
