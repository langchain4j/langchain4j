package dev.langchain4j.store.embedding.mariadb;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.mariadb.jdbc.MariaDbDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MariaDB EmbeddingStore Implementation
 * <p>
 * Using cosine or Euclidean similarity
 *
 */
public class MariaDbEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final Logger log = LoggerFactory.getLogger(MariaDbEmbeddingStore.class);

    /**
     * Datasource used to create the store
     */
    private final DataSource datasource;

    /**
     * Embeddings table name
     */
    private final String table;

    /**
     * distance
     */
    private final MariaDBDistanceType distanceType;

    private final String idFieldName;
    private final String embeddingFieldName;
    private final String contentFieldName;

    public static final String DEFAULT_TABLE_NAME = "vector_store";

    public static final String DEFAULT_COLUMN_EMBEDDING = "embedding";

    public static final String DEFAULT_COLUMN_ID = "id";

    public static final String DEFAULT_COLUMN_CONTENT = "content";

    /**
     * Metadata handler
     */
    final MetadataHandler metadataHandler;

    /**
     * Constructor for MariaDbEmbeddingStore Class
     *
     * @param datasource            The datasource to use
     * @param builder               builder
     */
    private MariaDbEmbeddingStore(DataSource datasource, Builder builder) {
        this.datasource = ensureNotNull(datasource, "datasource");
        this.table = validateAndEnquoteIdentifier(builder.table, DEFAULT_TABLE_NAME);
        this.contentFieldName = validateAndEnquoteIdentifier(builder.contentFieldName, DEFAULT_COLUMN_CONTENT);
        this.embeddingFieldName = validateAndEnquoteIdentifier(builder.embeddingFieldName, DEFAULT_COLUMN_EMBEDDING);
        this.idFieldName = validateAndEnquoteIdentifier(builder.idFieldName, DEFAULT_COLUMN_ID);

        MetadataStorageConfig config =
                getOrDefault(builder.metadataStorageConfig, DefaultMetadataStorageConfig.defaultConfig());
        this.metadataHandler = MetadataHandlerFactory.get(config, this.datasource);
        this.distanceType = builder.distanceType == null ? MariaDBDistanceType.COSINE : builder.distanceType;
        int dimension = ensureNotNull(builder.dimension, "dimension");
        initTable(builder.dropTableFirst, builder.createTable, dimension);
    }

    private String validateAndEnquoteIdentifier(String value, String defaultValue) {
        return value == null || value.isEmpty()
                ? defaultValue
                : MariaDbValidator.validateAndEnquoteIdentifier(value, false);
    }

    /**
     * Initialize metadata table following configuration
     *
     * @param dropTableFirst Should drop table first, usually for testing
     * @param createTable    Should create table automatically
     * @param dimension      The vector dimension
     */
    protected void initTable(boolean dropTableFirst, boolean createTable, int dimension) {
        String query = "init";
        try (Connection connection = datasource.getConnection();
                Statement statement = connection.createStatement()) {
            if (dropTableFirst) {
                statement.executeUpdate("DROP TABLE IF EXISTS " + table);
            }
            if (createTable) {
                query = String.format(
                        "CREATE TABLE IF NOT EXISTS %s ("
                                + "%s UUID NOT NULL DEFAULT uuid() PRIMARY KEY, "
                                + "%s VECTOR(%s) NOT NULL, "
                                + "%s TEXT NULL, "
                                + "%s, "
                                + "VECTOR INDEX %s_idx (%s) "
                                + ") ENGINE=InnoDB COLLATE uca1400_ai_cs",
                        table,
                        idFieldName,
                        embeddingFieldName,
                        ensureGreaterThanZero(dimension, "dimension"),
                        contentFieldName,
                        metadataHandler.columnDefinitionsString(),
                        (table + "_" + embeddingFieldName).replaceAll("[ \\`\"'\\\\\\P{Print}]", ""),
                        embeddingFieldName);
                statement.executeUpdate(query);
                metadataHandler.createMetadataIndexes(statement, table);
            }

        } catch (SQLException e) {
            throw new RuntimeException(String.format("Failed to execute '%s'", query), e);
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
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).toList();
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
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).toList();
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        addAllInternal(ids, embeddings, embedded);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        try (Connection connection = datasource.getConnection();
                Statement statement = connection.createStatement()) {
            // ensure ids are UUID to avoid injection
            String commaSeparated = ids.stream()
                    .map(UUID::fromString)
                    .map(uuid -> "'" + uuid + "'")
                    .collect(Collectors.joining(","));

            String sql = String.format("DELETE FROM %s WHERE %s IN (%s)", table, idFieldName, commaSeparated);
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        String whereClause = metadataHandler.whereClause(filter);
        String sql = String.format("DELETE FROM %s WHERE %s", table, whereClause);
        try (Connection connection = datasource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll() {
        try (Connection connection = datasource.getConnection();
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

        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        try (Connection connection = datasource.getConnection()) {

            String metadataFilterClause = filter != null ? metadataHandler.whereClause(filter) : null;
            String filterClause = "";
            if (metadataFilterClause != null && !metadataFilterClause.isEmpty()) {
                filterClause = "and " + metadataFilterClause + " ";
            }

            String distanceTypeName = this.distanceType.name().toLowerCase(Locale.ROOT);

            final String sql = String.format(
                    "SELECT * FROM (select %s, %s, %s, (2 - vec_distance_%s(%s, ?)) / 2 as"
                            + " score, %s from %s) as t where score >= ? %sorder by score desc"
                            + " LIMIT %s",
                    idFieldName,
                    embeddingFieldName,
                    contentFieldName,
                    distanceTypeName,
                    embeddingFieldName,
                    String.join(",", metadataHandler.escapedColumnsName()),
                    table,
                    filterClause,
                    maxResults);

            try (PreparedStatement selectStmt = connection.prepareStatement(sql)) {
                selectStmt.setObject(1, referenceEmbedding.vector());
                selectStmt.setDouble(2, minScore);

                try (ResultSet resultSet = selectStmt.executeQuery()) {
                    while (resultSet.next()) {
                        String embeddingId = resultSet.getString(1);
                        Embedding embedding = new Embedding(resultSet.getObject(2, float[].class));
                        String text = resultSet.getString(3);
                        double score = resultSet.getDouble(4);

                        TextSegment textSegment = null;
                        if (isNotNullOrBlank(text)) {
                            Metadata metadata = metadataHandler.fromResultSet(resultSet);
                            textSegment = TextSegment.from(text, metadata);
                        }
                        result.add(new EmbeddingMatch<>(score, embeddingId, embedding, textSegment));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return new EmbeddingSearchResult<>(result);
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(
                Collections.singletonList(id),
                Collections.singletonList(embedding),
                embedded == null ? null : Collections.singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        try (Connection connection = datasource.getConnection()) {
            String query = String.format(
                    "INSERT INTO %s (%s, %s, %s, %s) VALUES (?, ?, ?, %s) "
                            + "ON DUPLICATE KEY UPDATE %s = VALUES(%s), %s = VALUES(%s)%s",
                    table,
                    idFieldName,
                    embeddingFieldName,
                    contentFieldName,
                    String.join(",", metadataHandler.escapedColumnsName()),
                    String.join(
                            ",",
                            Collections.nCopies(
                                    metadataHandler.escapedColumnsName().size(), "?")),
                    embeddingFieldName,
                    embeddingFieldName,
                    contentFieldName,
                    contentFieldName,
                    metadataHandler.insertClause());
            try (PreparedStatement upsertStmt = connection.prepareStatement(query)) {
                for (int i = 0; i < ids.size(); ++i) {
                    upsertStmt.setString(1, ids.get(i));
                    upsertStmt.setObject(2, embeddings.get(i).vector());

                    if (embedded != null && embedded.get(i) != null) {
                        upsertStmt.setString(3, embedded.get(i).text());
                        metadataHandler.setMetadata(
                                upsertStmt, 4, embedded.get(i).metadata());
                    } else {
                        upsertStmt.setNull(3, Types.VARCHAR);
                        IntStream.range(
                                        4,
                                        4 + metadataHandler.escapedColumnsName().size())
                                .forEach(j -> {
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String table;
        private MariaDBDistanceType distanceType;
        private String idFieldName;
        private String embeddingFieldName;
        private String contentFieldName;
        private MetadataStorageConfig metadataStorageConfig;
        private boolean dropTableFirst;
        private boolean createTable = false;
        private Integer dimension;
        private DataSource datasource;
        private String url;
        private String user;
        private String password;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder datasource(DataSource datasource) {
            this.datasource = datasource;
            return this;
        }

        public Builder table(String table) {
            this.table = table;
            return this;
        }

        public Builder distanceType(MariaDBDistanceType distanceType) {
            this.distanceType = distanceType;
            return this;
        }

        public Builder idFieldName(String idFieldName) {
            this.idFieldName = idFieldName;
            return this;
        }

        public Builder embeddingFieldName(String embeddingFieldName) {
            this.embeddingFieldName = embeddingFieldName;
            return this;
        }

        public Builder contentFieldName(String contentFieldName) {
            this.contentFieldName = contentFieldName;
            return this;
        }

        public Builder metadataStorageConfig(MetadataStorageConfig metadataStorageConfig) {
            this.metadataStorageConfig = metadataStorageConfig;
            return this;
        }

        public Builder dropTableFirst(boolean dropTableFirst) {
            this.dropTableFirst = dropTableFirst;
            return this;
        }

        public Builder createTable(boolean createTable) {
            this.createTable = createTable;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public MariaDbEmbeddingStore build() {
            if (datasource == null) {
                if (url == null) {
                    throw new IllegalArgumentException("set datasource or url ");
                }
                MariaDbDataSource ds = new MariaDbDataSource();
                try {
                    ds.setUrl(this.url);
                    ds.setUser(this.user);
                    ds.setPassword(this.password);
                } catch (SQLException e) {
                    throw new IllegalArgumentException("Wrong url configuring builder: '%s'".formatted(url), e);
                }
                datasource = ds;
            }
            return new MariaDbEmbeddingStore(datasource, this);
        }
    }
}
