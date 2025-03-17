package dev.langchain4j.store.embedding.pgvector;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pgvector.PGvector;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * PGVector 0.29 EmbeddingStore Implementation
 * <p>
 * Only cosine similarity is used.
 * Only ivfflat index is used.
 */
class PgVectorEmbeddingStore029 implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(PgVectorEmbeddingStore.class);

    private static final Gson GSON = new Gson();

    private final String host;
    private final Integer port;
    private final String user;
    private final String password;
    private final String database;
    private final String table;

    /**
     * All args constructor for PgVectorEmbeddingStore Class
     *
     * @param host           The database host
     * @param port           The database port
     * @param user           The database user
     * @param password       The database password
     * @param database       The database name
     * @param table          The database table
     * @param dimension      The vector dimension
     * @param useIndex       Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param indexListSize  The IVFFlat number of lists
     * @param createTable    Should create table automatically
     * @param dropTableFirst Should drop table first, usually for testing
     */
    @Builder
    PgVectorEmbeddingStore029(
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
            Boolean dropTableFirst) {
        this.host = ensureNotBlank(host, "host");
        this.port = ensureGreaterThanZero(port, "port");
        this.user = ensureNotBlank(user, "user");
        this.password = ensureNotBlank(password, "password");
        this.database = ensureNotBlank(database, "database");
        this.table = ensureNotBlank(table, "table");

        useIndex = getOrDefault(useIndex, false);
        createTable = getOrDefault(createTable, true);
        dropTableFirst = getOrDefault(dropTableFirst, false);

        try (Connection connection = setupConnection()) {

            if (dropTableFirst) {
                connection.createStatement().executeUpdate("DROP TABLE IF EXISTS %s".formatted(table));
            }

            if (createTable) {
                connection.createStatement().executeUpdate((
                        "CREATE TABLE IF NOT EXISTS %s (" +
                                "embedding_id UUID PRIMARY KEY, " +
                                "embedding vector(%s), " +
                                "text TEXT NULL, " +
                                "metadata JSON NULL" +
                                ")").formatted(
                        table, ensureGreaterThanZero(dimension, "dimension")));
            }

            if (useIndex) {
                final String indexName = table + "_ivfflat_index";
                connection.createStatement().executeUpdate((
                        "CREATE INDEX IF NOT EXISTS %s ON %s " +
                                "USING ivfflat (embedding vector_cosine_ops) " +
                                "WITH (lists = %s)").formatted(
                        indexName, table, ensureGreaterThanZero(indexListSize, "indexListSize")));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Connection setupConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:postgresql://%s:%s/%s".formatted(host, port, database),
                user,
                password
        );
        connection.createStatement().executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
        PGvector.addVectorType(connection);
        return connection;
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
        addAll(ids, embeddings, embedded);
        return ids;
    }

    /**
     * Finds the most relevant (closest in space) embeddings to the provided reference embedding.
     *
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this one.
     * @param maxResults         The maximum number of embeddings to be returned.
     * @param minScore           The minimum relevance score, ranging from 0 to 1 (inclusive).
     *                           Only embeddings with a score of this value or higher will be returned.
     * @return A list of embedding matches.
     * Each embedding match includes a relevance score (derivative of cosine distance),
     * ranging from 0 (not relevant) to 1 (highly relevant).
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        try (Connection connection = setupConnection()) {
            String referenceVector = Arrays.toString(referenceEmbedding.vector());
            String query = "WITH temp AS (SELECT (2 - (embedding <=> '%s')) / 2 AS score, embedding_id, embedding, text, metadata FROM %s) SELECT * FROM temp WHERE score >= %s ORDER BY score desc LIMIT %s;".formatted(
                    referenceVector, table, minScore, maxResults);
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
                    String metadataJson = Optional.ofNullable(resultSet.getString("metadata")).orElse("{}");
                    Type type = new TypeToken<Map<String, String>>() {
                    }.getType();
                    Metadata metadata = new Metadata(new HashMap<>(GSON.fromJson(metadataJson, type)));
                    textSegment = TextSegment.from(text, metadata);
                }

                result.add(new EmbeddingMatch<>(score, embeddingId, embedding, textSegment));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return result;
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

        try (Connection connection = setupConnection()) {
            String query = (
                    "INSERT INTO %s (embedding_id, embedding, text, metadata) VALUES (?, ?, ?, ?)" +
                            "ON CONFLICT (embedding_id) DO UPDATE SET " +
                            "embedding = EXCLUDED.embedding," +
                            "text = EXCLUDED.text," +
                            "metadata = EXCLUDED.metadata;").formatted(
                    table);

            PreparedStatement upsertStmt = connection.prepareStatement(query);

            for (int i = 0; i < ids.size(); ++i) {
                upsertStmt.setObject(1, UUID.fromString(ids.get(i)));
                upsertStmt.setObject(2, new PGvector(embeddings.get(i).vector()));

                if (embedded != null && embedded.get(i) != null) {
                    upsertStmt.setObject(3, embedded.get(i).text());
                    Map<String, String> metadata = embedded.get(i).metadata().asMap();
                    upsertStmt.setObject(4, GSON.toJson(metadata), Types.OTHER);
                } else {
                    upsertStmt.setNull(3, Types.VARCHAR);
                    upsertStmt.setNull(4, Types.OTHER);
                }
                upsertStmt.addBatch();
            }

            upsertStmt.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
