package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.*;
import dev.langchain4j.store.embedding.filter.Filter;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.OracleType;
import oracle.jdbc.OracleTypes;
import oracle.sql.json.OracleJsonDecimal;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonValue;
import oracle.sql.json.OracleJsonValue.OracleJsonType;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * <p>
 * An <code>EmbeddingStore</code> which uses AI Vector Search capabilities of Oracle Database.
 * </p><p>
 *
 * </p>
 */
public final class OracleEmbeddingStore implements EmbeddingStore<TextSegment> {

    /**
     * The mapping function for use with {@link SQLFilter#asSQLExpression(UnaryOperator)}. The identified value is extracted
     * from the metadata JSON column by passing a JSON path expression to the JSON_VALUE function.
     */
    private static final UnaryOperator<String> METADATA_KEY_MAPPER = id -> "JSON_VALUE(metadata, '$." + id + "')";

    /** DataSource configured to connect with an Oracle Database. */
    private final DataSource dataSource;

    /** Name of a database table accessed by this embedding store */
    private final String tableName;

    /**
     * Constructs embedding store configured by a builder.
     *
     * @param builder Builder that configures the emebedding store. Not null.
     *
     * @throws IllegalArgumentException If the configuration is not valid.
     */
    private OracleEmbeddingStore(Builder builder) {
        this.dataSource = ensureNotNull(builder.dataSource, "dataSource");
        this.tableName = ensureNotNull(builder.tableName, "tableName");

        createSchema(dataSource, tableName);
    }

    /**
     * <p>
     * Creates database tables, indexes, and any other schema objects needed to store embeddings. The schema objectsif it does not exist already.
     * </p><p>
     * The table uses a VARCHAR(36) column as a primary key. This data type is chosen for consistency with other
     * embedding store implementations which accept {@link UUID#toString()} as an id.
     * </p><p>
     * Embeddings are stored as VECTOR having any length of FLOAT32 dimensions. The FLOAT32 type can store any number
     * represented in the <code>float[]</code> returned by {@link Embedding#vector()}. A NOT NULL constraint conforms
     * with the behavior other embedding store implementations which do not accept NULL embeddings for "add" operations.
     * </p><p>
     * The text is stored as CLOB, allowing text of any length to be stored (versus VARCHAR which is limited to 32k
     * characters). A string returned by {@link TextSegment#text()} can be up to 2G characters.
     * </p><p>
     * The metadata is stored as JSON. The unstructured JSON type can store the unstructured metadata returned by
     * {@link TextSegment#metadata()}.
     * </p><p>
     * A vector index is created on the embedding column to speed up similarity search queries. The vector
     * index uses a cosine distance, which is the same metric used by the {@link #search(EmbeddingSearchRequest)}
     * method.
     * </p><p>
     * The vector index type is an inverted flat file (IVF), rather than hierarchical navigable small world (HNSW),
     * because DML operations are not possible after creating an HNSW index. Methods like {@link #add(Embedding)}
     * require the use of DML operations.
     * </p><p>
     * There are many parameters which can tune the index, but none are set for now. Later work may tune the index for
     * operations of a an embedding store.
     * tuned  operations
     * values have these parameters set to any non-default values that would tuned for the specific workloads  when possible. Later work can also allow users to configure the {@link Builder} with a custom CREATE VECTOR INDEX
     * command. This would allow complete control over the parameters, even allowing for HNSW type indexes in read-only
     * cases.
     * </p><p>
     * The vector index uses a cosine distance.
     * </p>
     *
     * @param dataSource
     *
     * @param tableName
     *
     * @throws IllegalStateException If connection to the database fails, or an error occurs when creating the table.
     */
    private static void createSchema(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()
        ) {

            statement.addBatch("CREATE TABLE IF NOT EXISTS " + tableName
                    + "(id VARCHAR(36) NOT NULL,"
                    + " embedding VECTOR(*, FLOAT32) NOT NULL,"
                    + " content CLOB,"
                    + " metadata JSON,"
                    + " PRIMARY KEY (id))");

            statement.addBatch("CREATE VECTOR INDEX IF NOT EXISTS " + tableName + "_vector_index" +
                    " ON " +tableName + "(embedding)" +
                    " ORGANIZATION NEIGHBOR PARTITIONS");

            statement.executeBatch();
        }
        catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }

    }

    @Override
    public String add(Embedding embedding) {
        ensureNotNull(embedding, "embedding");
        List<String> id = addAll(Collections.singletonList(embedding));
        return id.get(0);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        ensureNotNull(embeddings, "embeddings");

        String[] ids = new String[embeddings.size()];
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO " + tableName + "(id, embedding) VALUES (?, ?)")
        ) {
           for (int i = 0; i < embeddings.size(); i++) {
               String id = randomUUID();
               ids[i] = id;

               Embedding embedding = ensureIndexNotNull(embeddings, i, "embeddings");

               insert.setString(1, id);
               insert.setObject(2, embedding.vector(), OracleType.VECTOR_FLOAT32);
               insert.addBatch();
           }
           insert.executeBatch();
        }
        catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }

        return Arrays.asList(ids);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        ensureNotNull(embedding, "embedding");
        ensureNotNull(textSegment, "textSegment");
        List<String> id = addAll(
                Collections.singletonList(embedding),
                Collections.singletonList(textSegment));
        return id.get(0);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        ensureNotNull(embeddings, "embeddings");
        ensureNotNull(embedded, "embedded");

        if (embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("embeddings.size() " + embeddings.size()
                    + " is not equal to embedded.size() " + embedded.size());
        }

        String[] ids = new String[embeddings.size()];

        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO " + tableName + "(id, embedding, content, metadata) VALUES (?, ?, ?, ?)")
        ) {

            for (int i = 0; i < embeddings.size(); i++) {
                String id = ids[i] = randomUUID();
                Embedding embedding = ensureIndexNotNull(embeddings, i, "embeddings");
                TextSegment textSegment = ensureIndexNotNull(embedded, i, "embedded");

                insert.setString(1, id);
                insert.setObject(2, embedding.vector(), OracleType.VECTOR_FLOAT32);
                insert.setObject(3, textSegment.text());
                insert.setObject(4, getOsonFromMetadata(textSegment.metadata()));
                insert.addBatch();
            }
            insert.executeBatch();

            return Arrays.asList(ids);
        }
        catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public void add(String id, Embedding embedding) {
        ensureNotNull(id, "id");
        ensureNotNull(embedding, "embedding");

        // A MERGE command allows the user to update an existing embedding. The JavaDoc does not specify what this
        // method should do in this case, but updating an existing row is consistent with other EmbeddingStore
        // implementations.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement merge = connection.prepareStatement(
                     "MERGE INTO " + tableName + " existing"
                             + " USING (SELECT ? as id, ? as embedding) new"
                             + " ON (new.id = existing.id)"
                             + " WHEN MATCHED THEN UPDATE SET existing.embedding = new.embedding"
                             + " WHEN NOT MATCHED THEN INSERT (id, embedding) VALUES (new.id, new.embedding)");
        ) {
            merge.setString(1, id);
            merge.setObject(2, embedding.vector(), OracleType.VECTOR_FLOAT32);
            merge.execute();
        }
        catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM " + tableName + " WHERE id = ?")
        ) {
            for (String id : ids) {
                ensureNotNull(id, "id");
                delete.setString(1, id);
                delete.addBatch();
            }
            delete.executeBatch();
        }
        catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");

        SQLFilter sqlFilter = SQLFilter.fromFilter(filter);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM " + tableName + sqlFilter.asWhereClause(METADATA_KEY_MAPPER))
        ) {
            sqlFilter.setParameters(delete, 1);
            delete.executeUpdate();
        }
        catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public void removeAll() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE " + tableName);
        }
        catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        ensureNotNull(request, "request");

        SQLFilter sqlFilter = SQLFilter.fromFilter(request.filter());

        final int maxResults = request.maxResults();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT VECTOR_DISTANCE(embedding, ?, COSINE) distance, id, embedding, content, metadata"
                             + " FROM " + tableName
                             + sqlFilter.asWhereClause(METADATA_KEY_MAPPER)
                             + " ORDER BY distance"
                             + " FETCH FIRST " + maxResults + " ROWS ONLY")
        ) {

            // Calls to defineColumnType reduce the number of network requests. When Oracle JDBC knows that it is
            // fetching VECTOR, CLOB, and/or JSON columns, the first request it sends to the database can include a LOB
            // prefetch size (VECTOR and JSON are value-based-lobs). If defineColumnType is not called, then JDBC needs
            // to send an additional request with the LOB prefetch size, after the first request has the database
            // respond with the column data types.
            OracleStatement oracleStatement = query.unwrap(OracleStatement.class);
            oracleStatement.defineColumnType(1, OracleTypes.BINARY_DOUBLE);
            oracleStatement.defineColumnType(2, OracleTypes.VARCHAR);
            oracleStatement.defineColumnType(3, OracleTypes.VECTOR_FLOAT32, 524308);
            oracleStatement.defineColumnType(4, OracleTypes.CLOB, Integer.MAX_VALUE);
            oracleStatement.defineColumnType(5, OracleTypes.JSON, Integer.MAX_VALUE);


            query.setObject(1, request.queryEmbedding().vector(), OracleTypes.VECTOR_FLOAT32);
            sqlFilter.setParameters(query, 2);

            query.setFetchSize(maxResults);

            double minScore = request.minScore();
            List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>(maxResults);
            try (ResultSet resultSet = query.executeQuery()) {
                while (resultSet.next()) {

                    // The cosine distance is number between 0 and 2. The larger the distance, the less similar.
                    // Subtract the distance from 1 to get a cosine similarity between -1 and 1.
                    double distance = 1d - resultSet.getDouble("distance");
                    double score = RelevanceScore.fromCosineSimilarity(distance);

                    if (score < minScore)
                        continue;

                    String id = resultSet.getString("id");
                    float[] embedding = resultSet.getObject("embedding", float[].class);
                    String content = resultSet.getString("content");
                    OracleJsonObject metadata = resultSet.getObject("metadata", OracleJsonObject.class);

                    EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(
                            score,
                            id,
                            new Embedding(embedding),
                            content == null ? null : new TextSegment(content, getMetadataFromOson(metadata)));
                    matches.add(match);
                }
            }
            return new EmbeddingSearchResult<>(matches);
        }
        catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    /**
     * Converts text metadata into an Oracle JSON Object (OSON).
     * <p>
     * Instances of <code>Metadata</code> stored in <code></code>
     * </p>
     * @param metadata Metadata to convert. May be null.
     *
     * @return OSON with field names and values as the metadata
     */
    private static OracleJsonObject getOsonFromMetadata(Metadata metadata) {

        if (metadata == null)
            return null;

        OracleJsonFactory factory = new OracleJsonFactory();
        OracleJsonObject object = factory.createObject();
        Map<String, Object> map = metadata.toMap();

        for (Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Metadata does not store null values
            if (value instanceof Number) {
                Number number = (Number)value;
                if (number instanceof Integer)
                    object.put(key, number.intValue());
                else if (number instanceof Long)
                    object.put(key, number.longValue());
                else if (number instanceof Float)
                    // There is no put(String, float) method, only a put(String, double)
                    object.put(key, factory.createFloat(number.floatValue()));
                else if (number instanceof Double)
                    object.put(key, number.doubleValue());
                else
                    throw unrecognizedMetadata(key, value);
            }
            else {
                // This branch is taken for both String and UUID objects. The getMetadataFromOson method will attempt to
                // parse the string back out as a UUID.
                object.put(key, value.toString());
            }
        }

        return object;
    }

    /**
     * Creates <code>Metadata</code> from OSON. This method will only handle OSON values that are created by
     * {@link #getOsonFromMetadata(Metadata)}.
     */
    private static Metadata getMetadataFromOson(OracleJsonObject oson) {

        if (oson == null)
            return null;

        Metadata metadata = new Metadata();
        for (Entry<String, OracleJsonValue> entry : oson.entrySet()) {
            String key = entry.getKey();
            OracleJsonValue value = entry.getValue();

            OracleJsonType type = value.getOracleJsonType();
            switch (type) {
                case STRING:
                    String string = value.asJsonString().getString();
                    try {
                        UUID uuid = UUID.fromString(string);
                        metadata.put(key, uuid);
                    }
                    catch (IllegalArgumentException notUUID) {
                        metadata.put(key, string);
                    }
                    break;
                case DECIMAL:
                    OracleJsonDecimal decimal = value.asJsonDecimal();
                    switch (decimal.getTargetType()) {
                        case INT:
                            metadata.put(key, decimal.intValue());
                            break;
                        case LONG:
                            metadata.put(key, decimal.longValue());
                            break;
                        default:
                            throw new IllegalStateException("Unexpected type: " + decimal.getTargetType());
                    }
                    break;
                case FLOAT:
                    metadata.put(key, value.asJsonFloat().floatValue());
                    break;
                case DOUBLE:
                    metadata.put(key, value.asJsonDouble().doubleValue());
                    break;
                default:
                    throw new IllegalStateException("Unexpected type: " + type);
            }
        }

        return metadata;
    }

    /**
     * Returns a runtime exception which conveys the same information as a given SQLException. Methods which can not
     * throw a checked exception use this method to convert it into an unchecked exception.
     * @param sqlException
     * @return
     */
    private static RuntimeException uncheckSQLException(SQLException sqlException) {
        return new IllegalStateException(sqlException);
    }

    /**
     * Checks if a List contains a null element, and throws an exception if so.
     *
     * @param list List to check. Not null.
     * @param index Index of the list to check.
     * @param name Name of list, for use in an error message.
     * @return The list element. Not null.
     * @param <T> Class of the element
     * @throws IllegalArgumentException If the list element is null.
     */
    private static <T> T ensureIndexNotNull(List<T> list, int index, String name) {
        T value = list.get(index);

        if (value != null)
            return value;

        throw new IllegalArgumentException("null entry at index " + index + " in " + name);
    }

    private static IllegalArgumentException unrecognizedMetadata(String key, Object value) {
        return new IllegalArgumentException(
                "Unrecognized object type in Metadata with key \"" + key + "\" and value \"" + value
                        + "\" of class " + value.getClass().getSimpleName());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DataSource dataSource;

        private String tableName;

        /**
         * Configures a data source that connects to an Oracle Database.
         *
         * @param dataSource Data source to configure. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        /**
         * Configures the name of an Oracle Database table where embeddings are stored and retrieved from.
         *
         * @param tableName Name of database table. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * Builds an embedding store with the configuration applied to this builder.
         *
         * @return A new embedding store. Not null.
         */
        public OracleEmbeddingStore build() {
            return new OracleEmbeddingStore(this);
        }
    }
}
