package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

class VectorIndex {

    /** Option which configures how the {@link #create(DataSource, EmbeddingTable)} method creates this index */
    private final CreateOption createOption;

    /** The distance metric used by this index */
    private final DistanceMetric distanceMetric;

    private VectorIndex(Builder builder) {
        this.createOption = builder.createOption;
        this.distanceMetric = builder.distanceMetric;
    }

    /**
     * Creates an index on an {@link EmbeddingTable#embeddingColumn()}, where the index is configured by the
     * {@link VectorIndex.Builder} of this VectorIndex. No index is created if the Builder was configured with
     * {@link CreateOption#CREATE_NONE}.
     *
     * @param dataSource Data source that connects to an Oracle Database where the table is (possibly) created.
     * @param embeddingTable Table of the embedding
     * @throws SQLException If an error prevents the index from being created.
     */
    void create(DataSource dataSource, EmbeddingTable embeddingTable) throws SQLException {
        if (createOption == CreateOption.CREATE_NONE)
            return;

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
        ) {
            String tableName = embeddingTable.name();
            String embeddingColumn = embeddingTable.embeddingColumn();

            // Build a name in the form of: "{tableName}_{embeddingColumn}_index"
            StringBuilder indexNameBuilder = new StringBuilder();

            // Check for a quoted table name or embedding column name
            boolean isQuoted = false;

            if (tableName.startsWith("\"") && tableName.endsWith("\"")) {
                indexNameBuilder.append(tableName, 1, tableName.length() - 1);
                isQuoted = true;
            }
            else {
                indexNameBuilder.append(tableName);
            }
            indexNameBuilder.append('_');

            if (embeddingColumn.startsWith("\"") && embeddingColumn.endsWith("\"")) {
                indexNameBuilder.append(embeddingColumn, 1, embeddingColumn.length() - 1);
                isQuoted = true;
            }
            else {
                indexNameBuilder.append(embeddingColumn);
            }
            indexNameBuilder.append("_index");

            // If the table or column name are a quoted identifiers, then the index name must also be quoted.
            if (isQuoted) {
                indexNameBuilder.insert(0, '"').append('"');
            }

            String indexName = indexNameBuilder.toString();

            if (createOption == CreateOption.CREATE_OR_REPLACE) {
                statement.addBatch("DROP INDEX IF EXISTS " + indexName);
            }

            statement.execute("CREATE VECTOR INDEX IF NOT EXISTS " + indexName +
                    " ON " + tableName + "(" + embeddingColumn + ")" +
                    " ORGANIZATION NEIGHBOR PARTITIONS" +
                    " WITH DISTANCE " + distanceMetric.name());

            statement.executeBatch();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    static class Builder {

        // These fields are specified by method level JavaDocs
        private CreateOption createOption = CreateOption.CREATE_NONE;
        private DistanceMetric distanceMetric = DistanceMetric.COSINE;

        private Builder(){}

        /**
         * Configures the option to create (or not create) an index. The default is {@link CreateOption#CREATE_NONE},
         * which means that no attempt is made to create an index .
         *
         * @param createOption Option for creating the index. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder createOption(CreateOption createOption) {
            ensureNotNull(createOption, "createOption");
            this.createOption = createOption;
            return this;
        }

        /**
         * Configures the distance metric of an index. The default metric is {@link DistanceMetric#COSINE}.
         * The metric configured by this method should match the one used to train the embedding model which generates
         * embeddings that the search method operates upon.
         *
         * @param distanceMetric Distance metric for an index. Not null.
         *
         * @return This builder. Not null.
         *
         * @throws IllegalArgumentException If the distanceMetric is null.
         */
        public Builder distanceMetric(DistanceMetric distanceMetric) {
            this.distanceMetric = ensureNotNull(distanceMetric, "distanceMetric");
            return this;
        }

        public VectorIndex build() {
            return new VectorIndex(this);
        }
    }
}
