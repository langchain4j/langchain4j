package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.internal.Utils.isCollectionEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pgvector.PGvector;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PGVector EmbeddingStore Implementation
 * <p>
 * Only cosine similarity is used.
 * Only ivfflat index is used.
 */
public class PgVectorEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(PgVectorEmbeddingStore.class);

    private static final Gson gson = new Gson();

    private final String dbHost;

    private final String dbUser;

    private final String dbPassword;

    private final String dbPort;

    private final String dbName;

    private final String namespace;

    /**
     * All args constructor for PgVectorEmbeddingStore Class
     *
     * @param dbHost The database host
     * @param dbUser The database user
     * @param dbPassword The database password
     * @param dbPort The database port
     * @param dbName The database name
     * @param namespace The vector namespace, as table name
     * @param dimension The vector dimension
     * @param useIndex Should use ivfflat index
     * @param indexListSize The ivfflat index size
     * @param createdTable Should create table
     * @param dropTableFirst Should drop table first, usually for testing
     */
    public PgVectorEmbeddingStore(
            @NonNull String dbHost,
            @NonNull String dbUser,
            @NonNull String dbPassword,
            @NonNull String dbPort,
            @NonNull String dbName,
            @NonNull String namespace,
            int dimension,
            boolean useIndex,
            int indexListSize,
            boolean createdTable,
            boolean dropTableFirst) {
        this.dbHost = dbHost;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.dbPort = dbPort;
        this.dbName = dbName;

        this.namespace = namespace;

        try (Connection connection = setupConnection()) {

            if (dropTableFirst) {
                Statement setupStmt = connection.createStatement();
                setupStmt.executeUpdate("DROP TABLE IF EXISTS %s".formatted(namespace));
            }

            if (createdTable) {
                Statement createStmt = connection.createStatement();
                createStmt.executeUpdate(
                        "CREATE TABLE %s (vector_id UUID PRIMARY KEY, embedding vector(%s), text TEXT NULL, metadata JSON NULL)"
                                .formatted(namespace, dimension));
            }

            if (useIndex) {
                Statement indexStmt = connection.createStatement();
                indexStmt.executeUpdate(
                        "CREATE INDEX ON %s USING ivfflat (embedding vector_cosine_ops) WITH (lists = %s)"
                                .formatted(namespace, indexListSize));
            }
        } catch (SQLException e) {
            throw new ServiceException("Init Failure", e.getCause());
        }
    }

    Connection setupConnection() throws SQLException {
        Connection connection =
                DriverManager.getConnection(
                        "jdbc:postgresql://%s:%s/%s"
                                .formatted(this.dbHost, this.dbPort, this.dbName),
                        this.dbUser,
                        this.dbPassword);
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
    public List<EmbeddingMatch<TextSegment>> findRelevant(
            Embedding referenceEmbedding, int maxResults, double minScore) {
        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        try (Connection conn = setupConnection()) {
            String referenceVector = Arrays.toString(referenceEmbedding.vector());
            String query =
                    "WITH temp AS (SELECT (2 - (embedding <=> '%s')) / 2 AS score, vector_id, embedding, text, metadata FROM %s) SELECT * FROM temp WHERE score >= %s ORDER BY score desc LIMIT %s;"
                            .formatted(referenceVector, this.namespace, minScore, maxResults);
            PreparedStatement selectStmt = conn.prepareStatement(query);

            ResultSet resultSet = selectStmt.executeQuery();
            while (resultSet.next()) {
                double score = resultSet.getDouble("score");
                String embeddingId = resultSet.getString("vector_id");

                PGvector vector = (PGvector) resultSet.getObject("embedding");
                Embedding embedding = new Embedding(vector.toArray());

                String text = resultSet.getString("text");
                String metadataJson =
                        Optional.ofNullable(resultSet.getString("metadata")).orElse("{}");
                Type type = new TypeToken<Map<String, String>>() {}.getType();

                Metadata metadata = new Metadata(new HashMap<>(gson.fromJson(metadataJson, type)));
                if (text == null || text.isBlank()) {
                    result.add(new EmbeddingMatch<>(score, embeddingId, embedding, null));
                } else {
                    TextSegment textSegment = TextSegment.from(text, metadata);
                    result.add(new EmbeddingMatch<>(score, embeddingId, embedding, textSegment));
                }
            }

        } catch (SQLException e) {
            throw new ServiceException("Neighbor Query Failure", e.getCause());
        }

        return result;
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(
                singletonList(id),
                singletonList(embedding),
                embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(
            List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isCollectionEmpty(ids) || isCollectionEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        try (Connection conn = setupConnection()) {
            String query =
                    "INSERT INTO %s (vector_id, embedding, text, metadata) VALUES (?, ?, ?, ?)"
                            .formatted(this.namespace);

            PreparedStatement upsertStmt = conn.prepareStatement(query);

            for (int i = 0; i < ids.size(); ++i) {
                upsertStmt.setObject(1, UUID.fromString(ids.get(i)));
                upsertStmt.setObject(2, new PGvector(embeddings.get(i).vector()));

                if (embedded != null && embedded.get(i) != null) {
                    upsertStmt.setObject(3, embedded.get(i).text());

                    Map<String, String> metadata =
                            new HashMap<>(embedded.get(i).metadata().asMap());
                    upsertStmt.setObject(4, gson.toJson(metadata), Types.OTHER);
                } else {
                    upsertStmt.setNull(3, Types.VARCHAR);
                    upsertStmt.setNull(4, Types.OTHER);
                }
                upsertStmt.addBatch();
            }

            upsertStmt.executeBatch();

        } catch (SQLException e) {
            throw new ServiceException("Insertion failure", e.getCause());
        }
    }
}
