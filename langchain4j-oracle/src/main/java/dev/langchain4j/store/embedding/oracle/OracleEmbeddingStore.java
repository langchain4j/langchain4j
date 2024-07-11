package dev.langchain4j.store.embedding.oracle;

import javax.sql.DataSource;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.Builder;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.OracleType;
import oracle.jdbc.OracleTypes;
import oracle.sql.json.OracleJsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;

/**
 * Oracle Database as a Langchain4j EmbeddingStore.
 * Similarity search currently supports both COSINE and DOT distance types when vectors are normalized.
 * Unless directed not to, the OracleEmbeddingStore will automatically create a vector table, indexing the embedding.
 * The embedding index uses index type IVF as of Oracle Database 23.4.
 */
public class OracleEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final Logger log = LoggerFactory.getLogger(OracleEmbeddingStore.class);
    private static final Integer DEFAULT_DIMENSIONS = -1;
    private static final Integer DEFAULT_ACCURACY = -1;
    private static final DistanceType DEFAULT_DISTANCE_TYPE = DistanceType.COSINE;
    private static final IndexType DEFAULT_INDEX_TYPE = IndexType.IVF;

    private final String table;
    private final DataSource dataSource;
    private final Integer accuracy;
    private final DistanceType distanceType;
    private final IndexType indexType;
    private final Boolean normalizeVectors;

    private final OracleJSONPathFilterMapper filterMapper = new OracleJSONPathFilterMapper();
    private final OracleDataAdapter dataAdapter = new OracleDataAdapter();

    /**
     *
     * @param dataSource Oracle Database JDBC DataSource.
     * @param table Vector table name.
     * @param dimension Embedding dimension.
     * @param accuracy Search accuracy. Not used unless specified.
     * @param distanceType Distance type to use for similarity search. Defaults to COSINE.
     * @param indexType Index type, currently supports IVF. Defaults to IVF.
     * @param useIndex Whether to create an index on the table. Defaults to false.
     * @param createTable Whether a table will be created on embedding store creation. Defaults to true.
     * @param dropTableFirst Whether the table will be dropped on embedding store creation. Defaults to false.
     *
     * @param normalizeVectors Whether vectors are normalized. Defaults to
     * false. If set to true, then instances of <code>Embedding</code> that are
     * provided as input to this <code>EmbeddingStore</code> may be modified
     * by invocations of {@link Embedding#normalize()}.
     *
     */
    @Builder
    public OracleEmbeddingStore(DataSource dataSource,
                                String table,
                                Integer dimension,
                                Integer accuracy,
                                DistanceType distanceType,
                                IndexType indexType,
                                Boolean useIndex,
                                Boolean createTable,
                                Boolean dropTableFirst,
                                Boolean normalizeVectors
    ) {
        this.dataSource = ensureNotNull(dataSource, "dataSource");
        this.table = ensureNotBlank(table, "table");
        this.accuracy = getOrDefault(accuracy, DEFAULT_ACCURACY);
        this.distanceType = getOrDefault(distanceType, DEFAULT_DISTANCE_TYPE);
        this.indexType = getOrDefault(indexType, DEFAULT_INDEX_TYPE);
        this.normalizeVectors = getOrDefault(normalizeVectors, false);

        useIndex = getOrDefault(useIndex, false);
        createTable = getOrDefault(createTable, true);
        dropTableFirst = getOrDefault(dropTableFirst, false);
        dimension = getOrDefault(dimension, DEFAULT_DIMENSIONS);

        initTable(dropTableFirst, createTable, useIndex, dimension);
    }


    /**
     * Adds a given embedding to the store.
     *
     * @param embedding
     *     The embedding to be added to the store.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding) {
        String id = UUID.randomUUID().toString();
        addInternal(id, embedding, null);
        return id;
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param id
     *     The unique identifier for the embedding to be added.
     * @param embedding
     *     The embedding to be added to the store.
     */
    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    /**
     * Adds a given embedding and the corresponding content that has been embedded
     * to the store.
     *
     * @param embedding
     *     The embedding to be added to the store.
     * @param textSegment
     *     Original content that was embedded.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = UUID.randomUUID().toString();
        addInternal(id, embedding, textSegment);
        return id;
    }

    /**
     * Adds multiple embeddings to the store.
     *
     * @param embeddings
     *     A list of embeddings to be added to the store.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = createIds(embeddings);
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    /**
     * Adds multiple embeddings and their corresponding contents that have been
     * embedded to the store.
     *
     * @param embeddings
     *     A list of embeddings to be added to the store.
     * @param embedded
     *     A list of original contents that were embedded.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings,
        List<TextSegment> embedded) {
        List<String> ids = createIds(embeddings);
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    /**
     * Removes a single embedding from the store by ID.
     *
     * @param id The unique ID of the embedding to be removed.
     */
    @Override
    public void remove(String id) {
        String deleteQuery = String.format("delete from %s where id = ?", table);
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete embedding " + id, e);
        }
    }

    /**
     * Removes all embeddings that match the specified IDs from the store.
     *
     * @param ids A collection of unique IDs of the embeddings to be removed.
     */
    @Override
    public void removeAll(Collection<String> ids) {
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE id =?")) {

            for (String s : ids) {
              delete.setString(1, s);
              delete.addBatch();
            }
            delete.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes all embeddings that match the specified {@link Filter} from the store.
     *
     * @param filter The filter to be applied to the {@link Metadata} of the {@link TextSegment} during removal.
     *               Only embeddings whose {@code TextSegment}'s {@code Metadata}
     *               match the {@code Filter} will be removed.
     */
    @Override
    public void removeAll(Filter filter) {
        String deleteQuery = String.format("delete from %s", table);
        if (filter != null) {
            deleteQuery = String.format("%s %s", deleteQuery, filterMapper.whereClause(filter));
        }
        try (Connection connection = dataSource.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(deleteQuery);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes all embeddings from the store.
     */
    @Override
    public void removeAll() {
        try (Connection connection = dataSource.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(String.format("truncate table %s", table));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        if (distanceType != DistanceType.COSINE && distanceType != DistanceType.DOT) {
            throw new UnsupportedOperationException("Similarity search for distance type " + distanceType + " not supported");
        }
        if (!normalizeVectors) {
            throw new UnsupportedOperationException("Similarity search vector normalization. See the 'normalizeVectors property of the OracleEmbeddingStore'");
        }

        Embedding requestEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        String filterClause = request.filter() != null ? filterMapper.whereClause(request.filter()) + "\n" : "";
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        String searchQuery = String.format("select * from\n" +
                "(\n" +
                "select id, content, metadata, embedding, (1 - %s) as score\n" +
                "from %s\n" +
                "%s" +
                "order by score desc\n" +
                ")\n" +
                "where score >= ?\n" +
                "%s", vectorDistanceClause(), table, filterClause, accuracyClause(maxResults));

        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement searchStatement = connection.prepareStatement(searchQuery)) {

            if (normalizeVectors)
                requestEmbedding.normalize();

            // This defineColumnType call lets Oracle JDBC know that it is about to query a VECTOR column. This
            // avoids an extra network request in which Oracle JDBC would have to send a VECTOR prefetch size before
            // fetching row data. Using a 524,308 byte prefetch, which is the same as what Oracle JDBC uses internally.
            searchStatement.unwrap(OracleStatement.class)
                    .defineColumnType(4, OracleTypes.VECTOR_FLOAT32, 524308);

            searchStatement.setFetchSize(maxResults);
            searchStatement.setObject(1, requestEmbedding.vector(), OracleTypes.VECTOR_FLOAT32);
            searchStatement.setObject(2, minScore, OracleTypes.NUMBER);
            try (ResultSet rs = searchStatement.executeQuery()) {

                while (rs.next()) {
                    String id = rs.getString("id");
                    float[] embeddings = rs.getObject("embedding", float[].class);

                    Embedding embedding = new Embedding(embeddings);
                    String content = rs.getObject("content", String.class);
                    double score = rs.getDouble("score");
                    TextSegment textSegment = null;

                    if (isNotNullOrBlank(content)) {
                        Map<String, Object> metadata = dataAdapter.toMap(rs.getObject("metadata", OracleJsonObject.class));
                        textSegment = TextSegment.from(content, new Metadata(metadata));
                    }

                    matches.add(new EmbeddingMatch<>(score, id, embedding, textSegment));
                }

                return new EmbeddingSearchResult<>(matches);
            }
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }

    }

    private String accuracyClause(int maxResults) {
        if (accuracy.equals(DEFAULT_ACCURACY)) {
            return String.format("fetch first %d rows only", maxResults);
        }
        return String.format("fetch approximate first %d rows only with target accuracy %d", maxResults, accuracy);
    }

    private String vectorDistanceClause() {
        String clause = String.format("vector_distance(embedding, ?, %s)", distanceType.name());
        if (distanceType == DistanceType.DOT) {
            clause = String.format("(1+%s)/2", clause);
        }
        return clause;
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(
                singletonList(id),
                singletonList(embedding),
                embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("Empty embeddings - none added");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids and embeddings have different size");
        ensureTrue(segments == null || segments.size() == embeddings.size(), "segments and embeddings have different size");

        String upsert = String.format("merge into %s target using (values(?, ?, ?, ?)) source (id, content, metadata, embedding) on (target.id = source.id)\n" +
                "when matched then update set target.content = source.content, target.metadata = source.metadata, target.embedding = source.embedding\n" +
                "when not matched then insert (target.id, target.content, target.metadata, target.embedding) values (source.id, source.content, source.metadata, source.embedding)",
                table);

        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement upsertStatement = connection.prepareStatement(upsert)) {

            for (int i = 0; i < ids.size(); i++) {
                upsertStatement.setString(1, ids.get(i));

                if (segments != null && segments.get(i) != null) {
                    TextSegment textSegment = segments.get(i);
                    upsertStatement.setString(2, textSegment.text());
                    OracleJsonObject ojson = dataAdapter.toJSON(textSegment.metadata().toMap());
                    upsertStatement.setObject(3, ojson, OracleTypes.JSON);
                } else {
                    upsertStatement.setNull(2, Types.VARCHAR);
                    upsertStatement.setObject(3, dataAdapter.toJSON(null), OracleType.JSON.getVendorTypeNumber());
                }

                Embedding embedding = embeddings.get(i);
                if (normalizeVectors)
                    embedding.normalize();

                upsertStatement.setObject(4, embedding.vector(), OracleTypes.VECTOR);
                upsertStatement.addBatch();
            }

            upsertStatement.executeBatch();
        } catch (SQLException sqlException) {
            throw new RuntimeException(sqlException);
        }
    }

    private List<String> createIds(List<Embedding> embeddings) {
        return embeddings.stream()
                .map(e -> UUID.randomUUID().toString())
                .collect(Collectors.toList());
    }

    /**
     * Creates the vector store table and index if specified.
     *
     * @param dropTableFirst Whether to drop the table before creation.
     * @param createTable Whether to create the table.
     * @param useIndex Whether to create an index on the table.
     * @param dimension The dimension of the vector store embeddings.
     */
    private void initTable(Boolean dropTableFirst, Boolean createTable, Boolean useIndex, Integer dimension) {
        try (
          Connection connection = dataSource.getConnection();
          Statement statement = connection.createStatement()) {

            if (dropTableFirst) {
                statement.addBatch(
                  String.format("drop table if exists %s purge", table));
            }

            if (createTable) {
                // The FLOAT32 dimension type of the embedding column aligns with the float[] storage type
                // used by Embedding objects.
                statement.addBatch(String.format(
                        "create table if not exists %s (\n" +
                                "id        varchar2(36) default sys_guid() primary key,\n" +
                                "content   clob,\n" +
                                "metadata  json,\n" +
                                "embedding vector(%s, FLOAT32) annotations(Distance '%s', IndexType '%s'))",
                        table, getDimensionString(dimension), distanceType.name(), indexType.name()));
            }

            if (useIndex) {
                switch (indexType) {
                    case IVF:
                        statement.addBatch(String.format("create vector index if not exists vector_index_%s on %s (embedding)\n" +
                                "organization neighbor partitions\n" +
                                "distance %s\n" +
                                "with target accuracy %d\n" +
                                "parameters (type IVF, neighbor partitions 10)",
                                table, table, distanceType.name(), getAccuracy()));
                        break;

                    /*
                     * TODO: Enable for 23.5 case HNSW:
                     * this.jdbcTemplate.execute(String.format(""" create vector index if not
                     * exists vector_index_%s on %s (embedding) organization inmemory neighbor
                     * graph distance %s with target accuracy %d parameters (type HNSW,
                     * neighbors 40, efconstruction 500)""", tableName, tableName,
                     * distanceType.name(), searchAccuracy == DEFAULT_SEARCH_ACCURACY ? 95 :
                     * searchAccuracy)); break;
                     */
                }
            }

            statement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create database table", e);
        }
    }

    private String getDimensionString(Integer dimension) {
        return dimension.equals(DEFAULT_DIMENSIONS) ? "*" : String.valueOf(dimension);
    }

    private int getAccuracy() {
        return accuracy.equals(DEFAULT_ACCURACY) ? 95 : accuracy;
    }

    /**
     * Specifies the distance type used for search.
     */
    public enum DistanceType {
        /**
         * Default metric. It calculates the cosine distance between two vectors.
         */
        COSINE,

        /**
         * Also called the inner product, calculates the negated dot product of two
         * vectors.
         */
        DOT,

        /**
         * Also called L2_DISTANCE, calculates the Euclidean distance between two vectors.
         */
        EUCLIDEAN,

        /**
         * Also called L2_SQUARED is the Euclidean distance without taking the square
         * root.
         */
        EUCLIDEAN_SQUARED,

        /*
         * Calculates the hamming distance between two vectors. Requires INT8 element
         * type.
         */
        // TODO: add HAMMING support,

        /**
         * Also called L1_DISTANCE or taxicab distance, calculates the Manhattan distance.
         */
        MANHATTAN
    }

    /**
     * Specifies the index type used on the embedding index.
     */
    public enum IndexType {

        /**
         * Performs exact nearest neighbor search.
         */
        NONE,

        /**
         * The default type of index created for an In-Memory Neighbor Graph vector index
         * is Hierarchical Navigable Small World (HNSW)
         * <p>
         * With Navigable Small World (NSW), the idea is to build a proximity graph where
         * each vector in the graph connects to several others based on three
         * characteristics:
         * <ul>
         * <li>The distance between vectors</li>
         * <li>The maximum number of closest vector candidates considered at each step of
         * the search during insertion (EFCONSTRUCTION)</li>
         * <li>Within the maximum number of connections (NEIGHBORS) permitted per
         * vector</li>
         * </ul>
         *
         * @see <a href=
         * "https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/understand-hierarchical-navigable-small-world-indexes.html">Oracle
         * Database documentation</a>
         */
        HNSW,

        /**
         * The default type of index created for a Neighbor Partition vector index is
         * Inverted File Flat (IVF) vector index. The IVF index is a technique designed to
         * enhance search efficiency by narrowing the search area through the use of
         * neighbor partitions or clusters.
         * <p>
         * * @see <a href=
         * "https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/understand-inverted-file-flat-vector-indexes.html">Oracle
         * Database documentation</a>
         */
        IVF;

    }

}