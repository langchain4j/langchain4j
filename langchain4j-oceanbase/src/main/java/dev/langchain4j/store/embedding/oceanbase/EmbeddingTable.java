package dev.langchain4j.store.embedding.oceanbase;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * Represents a database table where embeddings, text, and metadata are stored. The columns of this table are listed
 */
public final class EmbeddingTable {

    /**
     * Option which configures how the {@link #create(DataSource)} method creates this table
     */
    private final CreateOption createOption;

    /**
     * The name of this table
     */
    private final String name;

    /**
     * Name of a column which stores an id.
     */
    private final String idColumn;

    /**
     * Name of a column which stores an embedding.
     */
    private final String embeddingColumn;

    /**
     * Name of a column which stores text.
     */
    private final String textColumn;

    /**
     * Name of a column which stores metadata.
     */
    private final String metadataColumn;

    /**
     * The vector dimension
     */
    private final int vectorDimension;

    /**
     * The vector index name
     */
    private final String vectorIndexName;

    /**
     * The distance metric for vector similarity search
     */
    private final String distanceMetric;

    /**
     * The index type (e.g., hnsw, flat)
     */
    private final String indexType;

    private EmbeddingTable(Builder builder) {
        createOption = builder.createOption;
        name = builder.name;
        idColumn = builder.idColumn;
        embeddingColumn = builder.embeddingColumn;
        textColumn = builder.textColumn;
        metadataColumn = builder.metadataColumn;
        vectorDimension = builder.vectorDimension;
        vectorIndexName = builder.vectorIndexName;
        distanceMetric = builder.distanceMetric;
        indexType = builder.indexType;
    }

    /**
     * Creates a table configured by the {@link Builder} of this EmbeddingTable. No table is created if the Builder was
     * configured with {@link CreateOption#CREATE_NONE}.
     *
     * @param dataSource Data source that connects to an OceanBase Database where the table is (possibly) created.
     * @throws SQLException If an error prevents the table from being created.
     */
    void create(DataSource dataSource) throws SQLException {
        if (createOption == CreateOption.CREATE_NONE) return;

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (createOption == CreateOption.CREATE_OR_REPLACE) {
                statement.addBatch("DROP TABLE IF EXISTS " + name);
            }

            StringBuilder createTableSql = new StringBuilder();
            createTableSql.append("CREATE TABLE IF NOT EXISTS ").append(name)
                    .append("(").append(idColumn).append(" VARCHAR(36) NOT NULL, ")
                    .append(embeddingColumn).append(" VECTOR(").append(vectorDimension).append("), ")
                    .append(textColumn).append(" VARCHAR(4000), ")
                    .append(metadataColumn).append(" JSON, ")
                    .append("PRIMARY KEY (").append(idColumn).append("), ")
                    .append("VECTOR INDEX ").append(vectorIndexName).append("(")
                    .append(embeddingColumn).append(") WITH (distance=").append(distanceMetric)
                    .append(", type=").append(indexType).append("))");

            statement.addBatch(createTableSql.toString());
            statement.executeBatch();
        }
    }

    /**
     * Maps a metadata key to a JSON path expression for use in SQL queries.
     *
     * @param key Name of a metadata key. Not null.
     * @return A JSON path expression that returns the value of the key.
     */
    String mapMetadataKey(String key) {
        return "JSON_VALUE(" + metadataColumn + ", '$." + key + "')";
    }
    
    /**
     * Returns a MetadataKeyMapper that maps metadata keys to JSON path expressions.
     *
     * @return A metadata key mapper. Not null.
     */
    public MetadataKeyMapper getMetadataKeyMapper() {
        return key -> "JSON_VALUE(" + metadataColumn + ", '$." + key + "')";
    }

    /**
     * Returns the name of this table.
     *
     * @return Table name. Not null.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the name of the id column.
     *
     * @return Id column name. Not null.
     */
    public String idColumn() {
        return idColumn;
    }

    /**
     * Returns the name of the embedding column.
     *
     * @return Embedding column name. Not null.
     */
    public String embeddingColumn() {
        return embeddingColumn;
    }

    /**
     * Returns the name of the text column.
     *
     * @return Text column name. Not null.
     */
    public String textColumn() {
        return textColumn;
    }

    /**
     * Returns the name of the metadata column.
     *
     * @return Metadata column name. Not null.
     */
    public String metadataColumn() {
        return metadataColumn;
    }

    /**
     * Returns the dimension of the vector.
     *
     * @return Vector dimension.
     */
    public int vectorDimension() {
        return vectorDimension;
    }

    /**
     * Returns the name of the vector index.
     *
     * @return Vector index name. Not null.
     */
    public String vectorIndexName() {
        return vectorIndexName;
    }

    /**
     * Returns the distance metric used for vector similarity search.
     *
     * @return Distance metric. Not null.
     */
    public String distanceMetric() {
        return distanceMetric;
    }

    /**
     * Returns the index type.
     *
     * @return Index type. Not null.
     */
    public String indexType() {
        return indexType;
    }

    /**
     * Returns a builder for configuring an embedding table.
     *
     * @param tableName Name of the table. Not null.
     * @return A builder.
     */
    public static Builder builder(String tableName) {
        return new Builder(tableName);
    }

    /**
     * Builder for an EmbeddingTable.
     */
    public static final class Builder {

        private final String name;
        private String idColumn = "id";
        private String embeddingColumn = "embedding";
        private String textColumn = "text";
        private String metadataColumn = "metadata";
        private int vectorDimension = 1536;
        private String vectorIndexName = "idx_vector";
        private String distanceMetric = "L2";
        private String indexType = "hnsw";
        private CreateOption createOption = CreateOption.CREATE_IF_NOT_EXISTS;

        /**
         * Creates a builder for a table with a given name.
         *
         * @param tableName Name of the table. Not null.
         * @throws IllegalArgumentException If the table name is null.
         */
        private Builder(String tableName) {
            ensureNotNull(tableName, "tableName");
            this.name = tableName;
        }

        /**
         * Sets the column name for the id column. The default value is "id".
         *
         * @param columnName Column name. Not null.
         * @return This builder.
         * @throws IllegalArgumentException If the column name is null.
         */
        public Builder idColumn(String columnName) {
            ensureNotNull(columnName, "columnName");
            this.idColumn = columnName;
            return this;
        }

        /**
         * Sets the column name for the embedding column. The default value is "embedding".
         *
         * @param columnName Column name. Not null.
         * @return This builder.
         * @throws IllegalArgumentException If the column name is null.
         */
        public Builder embeddingColumn(String columnName) {
            ensureNotNull(columnName, "columnName");
            this.embeddingColumn = columnName;
            return this;
        }

        /**
         * Sets the column name for the text column. The default value is "text".
         *
         * @param columnName Column name. Not null.
         * @return This builder.
         * @throws IllegalArgumentException If the column name is null.
         */
        public Builder textColumn(String columnName) {
            ensureNotNull(columnName, "columnName");
            this.textColumn = columnName;
            return this;
        }

        /**
         * Sets the column name for the metadata column. The default value is "metadata".
         *
         * @param columnName Column name. Not null.
         * @return This builder.
         * @throws IllegalArgumentException If the column name is null.
         */
        public Builder metadataColumn(String columnName) {
            ensureNotNull(columnName, "columnName");
            this.metadataColumn = columnName;
            return this;
        }

        /**
         * Sets the dimensionality of the vector. The default value is 1536.
         *
         * @param dimension Vector dimension
         * @return This builder.
         * @throws IllegalArgumentException If the dimension is negative or zero.
         */
        public Builder vectorDimension(int dimension) {
            if (dimension <= 0) {
                throw new IllegalArgumentException("Vector dimension must be positive");
            }
            this.vectorDimension = dimension;
            return this;
        }

        /**
         * Sets the name of the vector index. The default value is "idx_vector".
         *
         * @param indexName Index name. Not null.
         * @return This builder.
         * @throws IllegalArgumentException If the index name is null.
         */
        public Builder vectorIndexName(String indexName) {
            ensureNotNull(indexName, "indexName");
            this.vectorIndexName = indexName;
            return this;
        }

        /**
         * Sets the distance metric for vector similarity search. The default value is "L2".
         *
         * @param metric Distance metric. Not null.
         * @return This builder.
         * @throws IllegalArgumentException If the metric is null.
         */
        public Builder distanceMetric(String metric) {
            ensureNotNull(metric, "metric");
            this.distanceMetric = metric;
            return this;
        }

        /**
         * Sets the index type. The default value is "hnsw".
         *
         * @param type Index type. Not null.
         * @return This builder.
         * @throws IllegalArgumentException If the type is null.
         */
        public Builder indexType(String type) {
            ensureNotNull(type, "type");
            this.indexType = type;
            return this;
        }

        /**
         * Sets the create option for the table.
         *
         * @param createOption Create option. Not null.
         * @return This builder.
         * @throws IllegalArgumentException If the create option is null.
         */
        public Builder createOption(CreateOption createOption) {
            ensureNotNull(createOption, "createOption");
            this.createOption = createOption;
            return this;
        }

        /**
         * Creates an EmbeddingTable with the configuration defined by this builder.
         *
         * @return A new EmbeddingTable. Not null.
         */
        public EmbeddingTable build() {
            return new EmbeddingTable(this);
        }
    }
}
