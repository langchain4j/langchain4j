package dev.langchain4j.store.embedding.pgvector;

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
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.lang.String.join;
import static java.util.Collections.nCopies;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * PGVector EmbeddingStore Implementation
 * <p>
 * Only cosine similarity is used.
 * Only ivfflat index is used.
 */
@NoArgsConstructor(force = true) // Needed for inherited bean injection validation
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
     * todo QueryMode
     */
    private final String regconfig;
    /**
     *
     */
    private final Boolean useFullTextIndex;

    /**
     * Constructor for PgVectorEmbeddingStore Class
     *
     * @param datasource            The datasource to use
     * @param table                 The database table
     * @param dimension             The vector dimension
     * @param useIndex              Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param useFullTextIndex      Should use <a href="https://www.postgresql.org/docs/current/gin-intro.html">GIN</a> for supporting full-text search and hybrid search
     * @param regconfig             The text search configuration <a href="https://www.postgresql.org/docs/9.4/functions-textsearch.html">Text Search Functions and Operators</a>
     * @param indexListSize         The IVFFlat number of lists
     * @param createTable           Should create table automatically
     * @param dropTableFirst        Should drop table first, usually for testing
     * @param metadataStorageConfig The {@link MetadataStorageConfig} config.
     */
    @Builder(builderMethodName = "datasourceBuilder", builderClassName = "DatasourceBuilder")
    protected PgVectorEmbeddingStore(DataSource datasource,
                                     String table,
                                     Integer dimension,
                                     Boolean useIndex,
                                     Boolean useFullTextIndex,
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
        this.useFullTextIndex = getOrDefault(useFullTextIndex, false);
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
     * @param useFullTextIndex      Should use <a href="https://www.postgresql.org/docs/current/gin-intro.html">GIN</a> for supporting full-text search and hybrid search
     * @param regconfig             The text search configuration <a href="https://www.postgresql.org/docs/9.4/functions-textsearch.html">Text Search Functions and Operators</a>
     * @param indexListSize         The IVFFlat number of lists
     * @param createTable           Should create table automatically
     * @param dropTableFirst        Should drop table first, usually for testing
     * @param metadataStorageConfig The {@link MetadataStorageConfig} config.
     */
    @SuppressWarnings("unused")
    @Builder
    protected PgVectorEmbeddingStore(
            String host,
            Integer port,
            String user,
            String password,
            String database,
            String table,
            Integer dimension,
            Boolean useIndex,
            Boolean useFullTextIndex,
            String regconfig,
            Integer indexListSize,
            Boolean createTable,
            Boolean dropTableFirst,
            MetadataStorageConfig metadataStorageConfig
    ) {
        this(createDataSource(host, port, user, password, database),
                table, dimension, useIndex, useFullTextIndex, regconfig, indexListSize, createTable, dropTableFirst, metadataStorageConfig);
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


    /**
     * Initialize metadata table following configuration
     *
     * @param dropTableFirst   Should drop table first, usually for testing
     * @param createTable      Should create table automatically
     * @param useIndex         Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param dimension        The vector dimension
     * @param indexListSize    The IVFFlat number of lists
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
            if (useFullTextIndex) {
                query = String.format("CREATE INDEX IF NOT EXISTS %s_text_search_idx ON %s USING GIN (text_tsv)",
                        table, table);
                statement.executeUpdate(query);
            }
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Failed to execute '%s'", query), e);
        }
    }

    private String buildCreateTableSQL(Integer dimension) {
        if (useFullTextIndex) { // need a tetevtor column to build index
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
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    /**
     * Adds multiple embeddings and their corresponding contents that have been embedded to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @param embedded   A list of original contents that were embedded.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, embedded);
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
                "WITH" + embeddingCTESQL(referenceVector, whereClause, false) +
                        String.format("SELECT * FROM embedding_result WHERE score >= %s ORDER BY score desc LIMIT %s;", minScore, maxResults);

        return searchInternal(query, i->{});
    }

    @Experimental
    public EmbeddingSearchResult<TextSegment> fullTextSearch(String content, Filter filter, int maxResults, double minScore) {
        String filterClause = (filter == null) ? "" : metadataHandler.whereClause(filter);
        String query =
                "WITH " + fullTextCTESQL(filterClause, false) +
                        "SELECT * FROM full_text_result WHERE  score >= ? ORDER BY score desc LIMIT ?;";

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
                "SELECT %s as score, " +
                "coalesce(full_text_result.embedding_id, embedding_result.embedding_id) as embedding_id, " +
                "coalesce(full_text_result.embedding, embedding_result.embedding) as embedding," +
                "coalesce(full_text_result.text, embedding_result.text) as text, " +
                metadataColumns +
                "FROM full_text_result FULL OUTER JOIN embedding_result USING (embedding_id) WHERE %s >= ? ORDER BY score desc LIMIT ?;";
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
                "embedding_result AS (SELECT " + scoreColumn +" AS score, embedding_id, embedding, text, %s FROM %s %s)\n",
                referenceVector, join(",", metadataHandler.columnsNames()), table, filterCondition);
    }

    private String fullTextCTESQL(String filterCondition, Boolean rankAsScore) {
        String scoreColumn = rankAsScore ? "row_number() over (order by ts_rank_cd(%s, %s, 32) desc)" :
                "ts_rank_cd(%s, %s, 32)";
        String toQuery = String.format("to_tsquery('%s', ?)", regconfig);
        String fullTextColumn = useFullTextIndex ? "text_tsv" : String.format("plainto_tsquery('%s', text)", regconfig);
        // 32 is normalization factor to make the score between 0 and 1. The score is calculated by rank / (rank + 1).
        return String.format(
                "full_text_result AS (SELECT " +scoreColumn+" AS score, embedding_id, embedding, text, %s FROM %s " +
                        "WHERE %s @@ %s %s)\n",
                fullTextColumn, toQuery, join(",", metadataHandler.columnsNames()), table,
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
        addAllInternal(
                singletonList(id),
                singletonList(embedding),
                embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(
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

}
