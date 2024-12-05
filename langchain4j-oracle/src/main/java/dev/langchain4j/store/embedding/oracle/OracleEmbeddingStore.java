package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import oracle.jdbc.OracleStatement;
import oracle.jdbc.OracleType;
import oracle.jdbc.OracleTypes;
import oracle.sql.json.OracleJsonDecimal;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonValue;
import oracle.sql.json.OracleJsonValue.OracleJsonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiFunction;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * <p>
 * An <code>EmbeddingStore</code> which uses AI Vector Search capabilities of Oracle Database. This embedding store
 * supports metadata filtering and removal
 * </p><p>
 * Instances of this store are created by configuring a builder:
 * </p><pre>{@code
 * EmbeddingStore<TextSegment> example(DataSource dataSource) {
 *   return OracleEmbeddingStore.builder()
 *     .dataSource(dataSource)
 *     .embeddingTable("example")
 *     .build();
 * }
 * }</pre><p>
 * It is recommended to configure a {@link DataSource} which pools connections, such as the Universal Connection Pool
 * (UCP) or Hikari. A connection pool will avoid the latency of repeatedly creating new database connections.
 * </p><p>
 * This embedding store requires a {@link EmbeddingTable} to be configured with {@link Builder#embeddingTable(String)}.
 * If the table does not already exist, it can be created by passing a {@link CreateOption} to
 * {@link Builder#embeddingTable(String, CreateOption)} or to {@link EmbeddingTable.Builder#createOption(CreateOption)}.
 * </p><p>
 * An inverted flat file (IVF) vector index is created on the embedding column. The index is named
 * "{tableName}_EMBEDDING_INDEX", where {tableName} is the name configured using the {@link Builder}.
 * </p><p>
 * Instances of this embedding store are safe for use by multiple threads.
 * </p>
 */
public final class OracleEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(OracleEmbeddingStore.class);
    /**
     * DataSource configured to connect with an Oracle Database.
     */
    private final DataSource dataSource;

    /**
     * Table where embeddings are stored.
     */
    private final EmbeddingTable table;

    /**
     * The mapping function for use with {@link SQLFilters#create(Filter, BiFunction)}. The function maps a
     * {@link Metadata} key to a field of the JSON "metadata" column. The builtin JSON_VALUE function is used to
     * evaluate a JSON path expression.
     */
    private final BiFunction<String, SQLType, String> metadataKeyMapper;

    /**
     * <code>true</code> if {@link #search(EmbeddingSearchRequest)} should use an exact search, or <code>false</code> if
     * it should use approximate search.
     */
    private final boolean isExactSearch;

    /**
     * Constructs embedding store configured by a builder.
     *
     * @param builder Builder that configures the embedding store. Not null.
     * @throws IllegalArgumentException If the configuration is not valid.
     * @implNote This constructor does not perform null checks. Validation should occur in {@link Builder#build()},
     * before calling this constructor.
     */
    private OracleEmbeddingStore(Builder builder) {
        dataSource = builder.dataSource;
        table = builder.embeddingTable;
        isExactSearch = builder.isExactSearch;
        metadataKeyMapper = (key, type) ->
                "JSON_VALUE(" + table.metadataColumn() + ", '$." + key + "' RETURNING " + type.getName() + ")";


        try {
            table.create(dataSource);
            createIndex(builder);
        } catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }

    }

    /**
     * Creates an index on the {@link EmbeddingTable#embeddingColumn()}, if configured to do so by
     * {@link Builder#vectorIndex(CreateOption)}.
     *
     * @param builder Builder that configures an embedding store. Not null.
     *
     * @throws SQLException If a database error prevents the index from being created.
     */
    private static void createIndex(Builder builder) throws SQLException {

        if (builder.vectorIndexCreateOption == CreateOption.CREATE_NONE)
            return;

        try (Connection connection = builder.dataSource.getConnection();
             Statement statement = connection.createStatement()
        ) {
            String tableName = builder.embeddingTable.name();

            // If the table name is a quoted identifier, then the index name must also be quoted.
            String indexName = tableName.startsWith("\"") && tableName.endsWith("\"")
                    ? "\"" + tableName.substring(1, tableName.length() - 1) + "_embedding_index\""
                    : tableName + "_embedding_index";

            if (builder.vectorIndexCreateOption == CreateOption.CREATE_OR_REPLACE)
                statement.addBatch("DROP INDEX IF EXISTS " + indexName);

            // The COSINE metric used here should match the VECTOR_DISTANCE metric of the search method.
            statement.addBatch("CREATE VECTOR INDEX IF NOT EXISTS " + indexName +
                        " ON " + tableName + "(" + builder.embeddingTable.embeddingColumn() + ")" +
                        " ORGANIZATION NEIGHBOR PARTITIONS" +
                        " WITH DISTANCE COSINE");

            statement.executeBatch();
        } catch (SQLException sqlException) {
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
                     "INSERT INTO " + table.name() + "(" +
                             table.idColumn() + ", " + table.embeddingColumn() + ") VALUES (?, ?)")
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
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ensureNotNull(embeddings, "embeddings");
        ensureNotNull(embedded, "embedded");

        if (embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("embeddings.size() " + embeddings.size()
                    + " is not equal to embedded.size() " + embedded.size());
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO " + table.name() + "(" +
                         String.join(", ",
                                 table.idColumn(), table.embeddingColumn(), table.textColumn(),
                                 table.metadataColumn()) +
                         ") VALUES (?, ?, ?, ?)")
        ) {

            for (int i = 0; i < embeddings.size(); i++) {
                Embedding embedding = ensureIndexNotNull(embeddings, i, "embeddings");
                TextSegment textSegment = ensureIndexNotNull(embedded, i, "embedded");

                insert.setString(1, ids.get(i));
                insert.setObject(2, embedding.vector(), OracleType.VECTOR_FLOAT32);
                insert.setObject(3, textSegment.text());
                insert.setObject(4, getOsonFromMetadata(textSegment.metadata()));
                insert.addBatch();
            }
            insert.executeBatch();

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
                     "MERGE INTO " + table.name() + " existing" +
                             " USING (SELECT ? as id, ? as embedding) new" +
                             " ON (new.id = existing." + table.idColumn() + ")" +
                             " WHEN MATCHED THEN UPDATE SET existing." + table.embeddingColumn() + " = new.embedding" +
                             " WHEN NOT MATCHED THEN INSERT (" +
                             table.idColumn() + ", " + table.embeddingColumn() +
                             ") VALUES (new.id, new.embedding)");
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
                     "DELETE FROM " + table.name() + " WHERE " + table.idColumn() + " = ?")
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

        SQLFilter sqlFilter = SQLFilters.create(filter, metadataKeyMapper);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM " + table.name() + sqlFilter.asWhereClause())
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
            statement.execute("TRUNCATE TABLE " + table.name());
        }
        catch (SQLException sqlException) {
            throw uncheckSQLException(sqlException);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        ensureNotNull(request, "request");

        SQLFilter sqlFilter = SQLFilters.create(request.filter(), metadataKeyMapper);
        final int maxResults = request.maxResults();

        // In a 23.4 build of Oracle Database, ORA-06553 will result if the distance column is referenced in the WHERE
        // clause. For this reason, the minScore filtering will happen locally. There are workarounds, such as binding
        // the VECTOR twice, or using a sub-select. These can be implemented if proven to offer a significant
        // performance improvement.
        // The COSINE metric used here should match the VECTOR INDEX metric of the createIndex method.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT VECTOR_DISTANCE(" +
                             table.embeddingColumn() + ", ?, COSINE) distance, " +
                             String.join(", ", table.idColumn(), table.embeddingColumn(), table.textColumn(),
                                     table.metadataColumn()) +
                         " FROM " + table.name() +
                         sqlFilter.asWhereClause() +
                         " ORDER BY distance" +
                         " FETCH " + (isExactSearch ? "" : " APPROXIMATE") +
                         " FIRST " + maxResults + " ROWS ONLY")
        ) {
            query.setObject(1, request.queryEmbedding().vector(), OracleTypes.VECTOR_FLOAT32);
            sqlFilter.setParameters(query, 2);
            query.setFetchSize(maxResults);

            // Calls to defineColumnType reduce the number of network requests. When Oracle JDBC knows that it is
            // fetching VECTOR, CLOB, and/or JSON columns, the first request it sends to the database can include a LOB
            // prefetch size (VECTOR and JSON are value-based-lobs). If defineColumnType is not called, then JDBC needs
            // to send an additional request with the LOB prefetch size, after the first request has the database
            // respond with the column data types.
            OracleStatement oracleStatement = query.unwrap(OracleStatement.class);
            oracleStatement.defineColumnType(1, OracleTypes.BINARY_DOUBLE);
            oracleStatement.defineColumnType(2, OracleTypes.VARCHAR);
            oracleStatement.defineColumnType(3, OracleTypes.VECTOR_FLOAT32, 524308); // <-- Max vector size, in bytes
            oracleStatement.defineColumnType(4, OracleTypes.CLOB, Integer.MAX_VALUE);
            oracleStatement.defineColumnType(5, OracleTypes.JSON, Integer.MAX_VALUE);

            List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>(maxResults);
            try (ResultSet resultSet = query.executeQuery()) {
                while (resultSet.next()) {

                    // Convert the cosine distance between 0 and 2 into a score between 1 and 0.
                    double score = 1d - (resultSet.getDouble("distance") / 2d);

                    // Local filtering of the minScore. See note about ORA-06553 above.
                    if (score < request.minScore())
                        break; // Break, because results are ordered by ascending distances.

                    String id = resultSet.getString(table.idColumn());
                    float[] embedding = resultSet.getObject(table.embeddingColumn(), float[].class);
                    String content = resultSet.getString(table.textColumn());
                    OracleJsonObject metadata = resultSet.getObject(table.metadataColumn(), OracleJsonObject.class);

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
     *
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
                // This branch is taken for both String, UUID, and any object that Metadata supports in the future. The
                // getMetadataFromOson method will attempt to parse the string back out as a UUID. The toJdbcType method
                // of SQLFilters assumes these objects are stored as a String in the OSON.
                object.put(key, value.toString());
            }
        }

        return object;
    }

    /**
     * Creates <code>Metadata</code> from OSON. Each value of the OSON is converted to the closest object type supported
     * by {@link Metadata}. In many cases, this will be a {@link String}. If the OSON is {@link OracleJsonObject#NULL},
     * then an empty {@link Metadata} is returned. Mapping NULL to empty is consistent with how {@link TextSegment}
     * represents metadata when it has no metadata; The {@link TextSegment#metadata()} method returns an empty MetaData.
     */
    private static Metadata getMetadataFromOson(OracleJsonObject oson) {
        Metadata metadata = new Metadata();

        if (oson == null)
            return metadata;

        for (Entry<String, OracleJsonValue> entry : oson.entrySet()) {
            String key = entry.getKey();
            OracleJsonValue value = entry.getValue();

            OracleJsonType type = value.getOracleJsonType();
            switch (type) {
                case STRING:
                    // The string may have originally been a java.util.UUID. Metadata.getUUID(...) can convert it back.
                    metadata.put(key, value.asJsonString().getString());
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
                            // There may be up to 38 digits of precision. String is used to avoid a lossy conversion.
                            metadata.put(key, decimal.toString());
                    }
                    break;
                case FLOAT:
                    metadata.put(key, value.asJsonFloat().floatValue());
                    break;
                case DOUBLE:
                    metadata.put(key, value.asJsonDouble().doubleValue());
                    break;
                default:
                    metadata.put(key, value.toString());
            }
        }

        return metadata;
    }

    /**
     * Returns a runtime exception which conveys the same information as a given SQLException. Methods which can not
     * throw a checked exception use this method to convert it into an unchecked exception.
     *
     * @param sqlException Exception thrown from the JDBC API. Not null.
     *
     * @return Unchecked exception to throw from the EmbeddingStore API. Not null.
     */
    private static RuntimeException uncheckSQLException(SQLException sqlException) {
        return sqlException instanceof BatchUpdateException
            ? uncheckSQLException((BatchUpdateException) sqlException)
            : new RuntimeException(sqlException);
    }

    /**
     * Returns a runtime exception which conveys the same information as a given BatchUpdateException. Methods which can
     * not throw a checked exception use this method to convert it into an unchecked exception. This is a specialized
     * form of {@link #uncheckSQLException(SQLException)} which extracts the first failure from
     * {@link BatchUpdateException#getNextException()}. This getNextException method returns more specific information,
     * which can help users debug. Future work on this method can handle cases where JDBC is configured to
     * {@linkplain oracle.jdbc.OracleConnection#CONNECTION_PROPERTY_CONTINUE_BATCH_ON_ERROR continue batches on error}
     * and can use {@link BatchUpdateException#getUpdateCounts()} to identify specific records that cause a failure.
     *
     * @param batchUpdateException Exception thrown from {@link PreparedStatement#executeBatch()}. Not null.
     *
     * @return Unchecked exception to throw from the EmbeddingStore API. Not null.
     */
    private static RuntimeException uncheckSQLException(BatchUpdateException batchUpdateException) {
        SQLException firstFailure = batchUpdateException.getNextException();
        return new RuntimeException(firstFailure == null ? batchUpdateException : firstFailure);
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

    /**
     * Returns a builder which configures and creates instances of {@link OracleEmbeddingStore}.
     *
     * @return A builder. Not null.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder which configures and creates instances of {@link OracleEmbeddingStore}.
     */
    public static class Builder {

        // All fields are specified by method-level JavaDocs

        private DataSource dataSource;
        private EmbeddingTable embeddingTable;
        private boolean isExactSearch = false;
        private CreateOption vectorIndexCreateOption = CreateOption.CREATE_NONE;

        private Builder() {}

        /**
         * Configures a data source that connects to an Oracle Database.
         *
         * @param dataSource Data source to configure. Not null.
         *
         * @return This builder. Not null.
         *
         * @throws IllegalArgumentException If the dataSource is null.
         */
        public Builder dataSource(DataSource dataSource) {
            this.dataSource = ensureNotNull(dataSource, "dataSource");
            return this;
        }

        /**
         * Configures the name of a table used to store embeddings, text, and metadata. The table must already exist and
         * have the {@linkplain EmbeddingTable default column names}.
         *
         * @param tableName The name of an existing table. Not null.
         *
         * @return The named embedding table. Not null;
         *
         * @throws IllegalArgumentException If the tableName is null.
         */
        public Builder embeddingTable(String tableName) {
            return embeddingTable(tableName, CreateOption.CREATE_NONE);
        }

        /**
         * Configures the name of table used to store embeddings, text, and metadata. Depending on which CreateOption is
         * provided, a table with the {@linkplain EmbeddingTable default column names} may be created when
         * {@link #build()} is called.
         *
         * @param tableName The name of an existing table. Not null.
         *
         * @param createOption Option for creating the table. Not null.
         *
         * @return This builder. Not null.
         *
         * @throws IllegalArgumentException If the tableName or createOption is null.
         */
        public Builder embeddingTable(String tableName, CreateOption createOption) {
            ensureNotNull(tableName, "tableName");
            ensureNotNull(createOption, "createOption");
            return embeddingTable(
                    EmbeddingTable.builder()
                            .name(tableName)
                            .createOption(createOption)
                            .build());
        }

        /**
         * Configures a table used to store embeddings, text, and metadata. Depending on which CreateOption the table is
         * {@link EmbeddingTable.Builder#createOption(CreateOption) configured} with, it may be created
         * when {@link #build()} is called.
         *
         * @param embeddingTable The table used to store embeddings. Not null.
         *
         * @return This builder. Not null.
         *
         * @throws IllegalArgumentException If the embeddingTable is null.
         */
        public Builder embeddingTable(EmbeddingTable embeddingTable) {
            ensureNotNull(embeddingTable, "embeddingTable");
            this.embeddingTable = embeddingTable;
            return this;
        }

        /**
         * Configures the creation of an index on the embedding column of the {@link EmbeddingTable} used by the
         * embedding store. Depending on which CreateOption is provided, an index may be created when {@link #build()}
         * is called. The default createOption is {@link CreateOption#CREATE_NONE}.
         *
         * @param createOption Option for creating the index. Not null.
         * @return This builder. Not null.
         */
        public Builder vectorIndex(CreateOption createOption) {
            ensureNotNull(createOption, "createOption");
            vectorIndexCreateOption = createOption;
            return this;
        }

        /**
         * Configures the embedding store to use
         * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/perform-exact-similarity-search.html">
         * exact
         * </a> or
         * <a href="https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/understand-approximate-similarity-search-using-vector-indexes.html">
         * approximate
         * </a>
         * similarity search. Approximate search is the default.
         *
         *
         * @param isExactSearch <code>true</code> to configure exact search, or <code>false</code> for approximate.
         *
         * @return This builder. Not null.
         */
        public Builder exactSearch(boolean isExactSearch) {
            this.isExactSearch = isExactSearch;
            return this;
        }

        /**
         * Builds an embedding store with the configuration applied to this builder.
         *
         * @return A new embedding store. Not null.
         *
         * @throws RuntimeException If connection to the database fails, or an error occurs when creating the schema
         * objects.
         */
        public OracleEmbeddingStore build() {
            // Validate that required options have been set
            ensureNotNull(dataSource, "dataSource");
            ensureNotNull(embeddingTable, "embeddingTable");
            return new OracleEmbeddingStore(this);
        }
    }
}
