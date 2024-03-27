package dev.langchain4j.store.embedding.pgvector;

import com.pgvector.PGvector;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
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
public class DataSourcePgVectorEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final Logger log = LoggerFactory.getLogger(DataSourcePgVectorEmbeddingStore.class);
    private final DataSource datasource;
    private final String table;
    private final MetadataHandler metadataHandler;

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
     * @param metadataConfig        The {@link MetadataConfig} config.
     */
    @Builder(builderMethodName = "withDataSourceBuilder")
    public DataSourcePgVectorEmbeddingStore(DataSource datasource,
                                            String table,
                                            Integer dimension,
                                            Boolean useIndex,
                                            Integer indexListSize,
                                            Boolean createTable,
                                            Boolean dropTableFirst,
                                            MetadataConfig metadataConfig) {
        this.datasource = datasource;
        this.table = ensureNotBlank(table, "table");
        MetadataConfig config = Optional.ofNullable(metadataConfig).orElse(DefaultMetadataConfig.defaultConfig());
        this.metadataHandler = MetadataHandlerFactory.get(config);
        useIndex = getOrDefault(useIndex, false);
        createTable = getOrDefault(createTable, true);
        dropTableFirst = getOrDefault(dropTableFirst, false);

        initTable(dropTableFirst, createTable, useIndex, dimension, indexListSize);
    }

    /**
     * Initialize metadata table following configuration
     * @param dropTableFirst    Should drop table first, usually for testing
     * @param createTable       Should create table automatically
     * @param useIndex          Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param dimension         The vector dimension
     * @param indexListSize     The IVFFlat number of lists
     */
    protected void initTable(Boolean dropTableFirst, Boolean createTable, Boolean useIndex, Integer dimension,
                        Integer indexListSize) {
        String query = "init";
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
            if (dropTableFirst) {
                statement.executeUpdate(String.format("DROP TABLE IF EXISTS %s", table));
            }
            if (createTable) {
                query = String.format("CREATE TABLE IF NOT EXISTS %s (embedding_id UUID PRIMARY KEY, " +
                                "embedding vector(%s), text TEXT NULL, %s )",
                        table, ensureGreaterThanZero(dimension, "dimension"),
                        metadataHandler.columnDefinition());
                statement.executeUpdate(query);
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
            metadataHandler.createMetadataIndexes(statement, table);
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Sql statement issue: %s \nLast query: %s", e, query));
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
     * @param id The unique identifier for the embedding to be added.
     * @param embedding The embedding to be added to the store.
     */
    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    /**
     * Adds a given embedding and the corresponding content that has been embedded to the store.
     *
     * @param embedding The embedding to be added to the store.
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
     * @param embedded A list of original contents that were embedded.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
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

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        try (Connection connection = getConnection()) {
            String referenceVector = Arrays.toString(referenceEmbedding.vector());
            String whereClause = (filter == null) ? "" : metadataHandler.whereClause(filter);
            whereClause = (whereClause.isEmpty()) ? "" : "WHERE " + whereClause;
            String query = String.format(
                    "WITH temp AS (SELECT (2 - (embedding <=> '%s')) / 2 AS score, embedding_id, embedding, text, " +
                            "%s FROM %s %s) SELECT * FROM temp WHERE score >= %s ORDER BY score desc LIMIT %s;",
                    referenceVector, metadataHandler.columnsNames(), table, whereClause, minScore, maxResults);
            PreparedStatement selectStmt = connection.prepareStatement(query);

            ResultSet resultSet = selectStmt.executeQuery();
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
            resultSet.close();
            selectStmt.close();
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
                    table, metadataHandler.columnsNames(),
                    join(",", nCopies(metadataHandler.nbMetadataColumns(), "?")),
                    metadataHandler.insertClause());
            PreparedStatement upsertStmt = connection.prepareStatement(query);

            for (int i = 0; i < ids.size(); ++i) {
                upsertStmt.setObject(1, UUID.fromString(ids.get(i)));
                upsertStmt.setObject(2, new PGvector(embeddings.get(i).vector()));

                if (embedded != null && embedded.get(i) != null) {
                    upsertStmt.setObject(3, embedded.get(i).text());
                    metadataHandler.setMetadata(upsertStmt, 4, embedded.get(i).metadata());
                } else {
                    upsertStmt.setNull(3, Types.VARCHAR);
                    IntStream.range(4, 4 + metadataHandler.nbMetadataColumns()).forEach(
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
            upsertStmt.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Datasource connection
     * @return Datasource connection
     * @throws SQLException exception
     */
    protected Connection getConnection() throws SQLException {
        Connection connection = datasource.getConnection();
        PGvector.addVectorType(connection); // Find a way to do it in connection creation.
        return connection;
    }
}
