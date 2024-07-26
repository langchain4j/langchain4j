package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public final class EmbeddingTable {

    /** Option which configures how the {@link #create(DataSource)} method creates this table */
    private final CreateOption createOption;

    /** The name of this table */
    private final String name;

    /** Name of a column which stores an id. */
    private final String idColumn;

    /** Name of a column which stores an embedding. */
    private final String embeddingColumn;

    /** Name of a column which stores text. */
    private final String textColumn;

    /** Name of a column which stores metadata. */
    private final String metadataColumn;

    private EmbeddingTable(Builder builder) {
        createOption = ensureNotNull(builder.createOption, "createOption");
        name = ensureNotNull(builder.name, "name");
        idColumn = ensureNotNull(builder.idColumn, "idColumn");
        embeddingColumn = ensureNotNull(builder.embeddingColumn, "embeddingColumn");
        textColumn = ensureNotNull(builder.textColumn, "textColumn");
        metadataColumn = ensureNotNull(builder.metadataColumn, "metadataColumn");
    }

    /**
     * <p>
     * Creates database tables, indexes, and any other schema objects needed to store embeddings. Any existing schema
     * objects are reused.
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
     * </p><p>
     * The vector index uses a cosine distance.
     * </p>
     *
     * @param dataSource DataSource which connects to a database. Not null.
     *
     * @throws SQLException If a database error prevents this table from being created.
     */
    void create(DataSource dataSource) throws SQLException {
        if (createOption == CreateOption.CREATE_NONE)
            return;

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
        ) {
            if (createOption == CreateOption.CREATE_OR_REPLACE)
                statement.addBatch("DROP TABLE IF EXISTS " + name);

            statement.addBatch("CREATE TABLE IF NOT EXISTS " + name
                    + "(" + idColumn + " VARCHAR(36) NOT NULL, "
                    + embeddingColumn + " VECTOR(*, FLOAT32) NOT NULL, "
                    + textColumn + " CLOB, "
                    + metadataColumn + " JSON, "
                    + "PRIMARY KEY (" + idColumn + "))");

            statement.executeBatch();
        }
    }

    /**
     * Returns the name of this table.
     *
     * @return The name of this table. Not null.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the name of this table's ID column. This column stores the auto-generated IDs returned by methods such as
     * {@link OracleEmbeddingStore#add(Embedding)}.
     *
     * @return The name of this table's ID column. Not null.
     */
    public String idColumn() {
        return idColumn;
    }

    /**
     * Returns the name of this table's embedding column. This column stores the {@link Embedding#vector()} when
     * Embedding objects are passed to methods such as {@link OracleEmbeddingStore#add(Embedding)}.
     *
     * @return The name of this table's embedding column. Not null.
     */
    public String embeddingColumn() {
        return embeddingColumn;
    }

    /**
     * Returns the name of this table's text column. This column stores the {@link TextSegment#text()} when
     * TextSegment objects are passed to methods such as {@link OracleEmbeddingStore#add(Embedding, TextSegment)}.
     *
     * @return The name of this table's text column. Not null.
     */
    public String textColumn() {
        return textColumn;
    }

    /**
     * Returns the name of this table's metadata column. This column stores the {@link TextSegment#metadata()} when
     * TextSegment objects are passed to methods such as {@link OracleEmbeddingStore#add(Embedding, TextSegment)}.
     *
     * @return The name of this table's text column. Not null.
     */
    public String metadataColumn() {
        return metadataColumn;
    }

    /**
     * Returns a builder that configures a new EmbeddingTable.
     *
     * @return An EmbeddingTable builder. Not null.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private CreateOption createOption = CreateOption.CREATE_NONE;

        private String name;

        private String idColumn = "id";

        private String embeddingColumn = "embedding";

        private String textColumn = "text";

        private String metadataColumn = "metadata";

        /**
         * Configures the option to create (or not create) a table. The default is {@link CreateOption#CREATE_NONE},
         * which means no attempt is made to create a table.
         *
         * @param createOption Name of the metadata column. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder createOption(CreateOption createOption) {
            this.createOption = createOption;
            return this;
        }

        /**
         * Configures the name of a table where embeddings are stored and retrieved from.
         *
         * @param name Name of database table. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Configures the name of a column which stores an id. The default name is "id".
         *
         * @param idColumn Name of the id column. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder idColumn(String idColumn) {
            this.idColumn = idColumn;
            return this;
        }

        /**
         * Configures the name of a column which stores an embedding. The default name is "embedding".
         *
         * @param embeddingColumn Name of the id column. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder embeddingColumn(String embeddingColumn) {
            this.embeddingColumn = embeddingColumn;
            return this;
        }

        /**
         * Configures the name of a column which stores text. The default name is "text".
         *
         * @param textColumn Name of the text column. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder textColumn(String textColumn) {
            this.textColumn = textColumn;
            return this;
        }

        /**
         * Configures the name of a column which stores metadata. The default name is "metadata".
         *
         * @param metadataColumn Name of the metadata column. Not null.
         *
         * @return This builder. Not null.
         */
        public Builder metadataColumn(String metadataColumn) {
            this.metadataColumn = metadataColumn;
            return this;
        }

        /**
         * Returns a new EmbeddingTable configured by this builder.
         *
         * @return A new EmbeddingTable. Not null.
         *
         * @throws IllegalArgumentException If this builder is missing any required configuration.
         */
        public EmbeddingTable build() {
            return new EmbeddingTable(this);
        }

    }
}
