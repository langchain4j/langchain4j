package dev.langchain4j.store.embedding.neo4j;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_AWAIT_INDEX_TIMEOUT;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_DATABASE_NAME;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_EMBEDDING_PROP;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_IDX_NAME;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_ID_PROP;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_LABEL;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_TEXT_PROP;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.EMBEDDINGS_ROW_KEY;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.PROPS;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.getRowsBatched;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.sanitizeOrThrows;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.toEmbeddingMatch;
import static java.util.Collections.singletonList;

/**
 * Represents a Vector index as an embedding store.
 * Annotated with `@Getter` to be used in {@link Neo4jEmbeddingUtils}
 */
public class Neo4jEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(Neo4jEmbeddingStore.class);

    /* Neo4j Java Driver settings */
    private final Driver driver;
    private final SessionConfig config;

    /* Neo4j schema field settings */
    private final int dimension;
    private final long awaitIndexTimeout;

    private final String indexName;
    private final String metadataPrefix;
    private final String embeddingProperty;
    private final String idProperty;
    private final String sanitizedEmbeddingProperty;
    private final String sanitizedIdProperty;
    private final String sanitizedText;
    private final String label;
    private final String sanitizedLabel;
    private final String textProperty;
    private final String databaseName;
    private final String retrievalQuery;
    private final Set<String> notMetaKeys;

    public static Neo4jEmbeddingStoreBuilder builder() {
        return new Neo4jEmbeddingStoreBuilder();
    }

    public Driver getDriver() {
        return this.driver;
    }

    public SessionConfig getConfig() {
        return this.config;
    }

    public int getDimension() {
        return this.dimension;
    }

    public long getAwaitIndexTimeout() {
        return this.awaitIndexTimeout;
    }

    public String getIndexName() {
        return this.indexName;
    }

    public String getMetadataPrefix() {
        return this.metadataPrefix;
    }

    public String getEmbeddingProperty() {
        return this.embeddingProperty;
    }

    public String getIdProperty() {
        return this.idProperty;
    }

    public String getSanitizedEmbeddingProperty() {
        return this.sanitizedEmbeddingProperty;
    }

    public String getSanitizedIdProperty() {
        return this.sanitizedIdProperty;
    }

    public String getSanitizedText() {
        return this.sanitizedText;
    }

    public String getLabel() {
        return this.label;
    }

    public String getSanitizedLabel() {
        return this.sanitizedLabel;
    }

    public String getTextProperty() {
        return this.textProperty;
    }

    public String getDatabaseName() {
        return this.databaseName;
    }

    public String getRetrievalQuery() {
        return this.retrievalQuery;
    }

    public Set<String> getNotMetaKeys() {
        return this.notMetaKeys;
    }

    /**
     * Creates an instance of Neo4jEmbeddingStore defining a {@link Driver}
     * starting from uri, user and password
     */
    public static class Neo4jEmbeddingStoreBuilder {
        private SessionConfig config;
        private Driver driver;
        private int dimension;
        private String label;
        private String embeddingProperty;
        private String idProperty;
        private String metadataPrefix;
        private String textProperty;
        private String indexName;
        private String databaseName;
        private String retrievalQuery;
        private long awaitIndexTimeout;

        Neo4jEmbeddingStoreBuilder() {
        }

        public Neo4jEmbeddingStoreBuilder withBasicAuth(String uri, String user, String password) {
            return this.driver(GraphDatabase.driver(uri, AuthTokens.basic(user, password)));
        }

        public Neo4jEmbeddingStoreBuilder config(SessionConfig config) {
            this.config = config;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder driver(Driver driver) {
            this.driver = driver;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder dimension(int dimension) {
            this.dimension = dimension;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder label(String label) {
            this.label = label;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder embeddingProperty(String embeddingProperty) {
            this.embeddingProperty = embeddingProperty;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder idProperty(String idProperty) {
            this.idProperty = idProperty;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder metadataPrefix(String metadataPrefix) {
            this.metadataPrefix = metadataPrefix;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder textProperty(String textProperty) {
            this.textProperty = textProperty;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder retrievalQuery(String retrievalQuery) {
            this.retrievalQuery = retrievalQuery;
            return this;
        }

        public Neo4jEmbeddingStoreBuilder awaitIndexTimeout(long awaitIndexTimeout) {
            this.awaitIndexTimeout = awaitIndexTimeout;
            return this;
        }

        public Neo4jEmbeddingStore build() {
            return new Neo4jEmbeddingStore(this.config, this.driver, this.dimension, this.label, this.embeddingProperty, this.idProperty, this.metadataPrefix, this.textProperty, this.indexName, this.databaseName, this.retrievalQuery, this.awaitIndexTimeout);
        }

        public String toString() {
            return "Neo4jEmbeddingStore.Neo4jEmbeddingStoreBuilder(config=" + this.config + ", driver=" + this.driver + ", dimension=" + this.dimension + ", label=" + this.label + ", embeddingProperty=" + this.embeddingProperty + ", idProperty=" + this.idProperty + ", metadataPrefix=" + this.metadataPrefix + ", textProperty=" + this.textProperty + ", indexName=" + this.indexName + ", databaseName=" + this.databaseName + ", retrievalQuery=" + this.retrievalQuery + ", awaitIndexTimeout=" + this.awaitIndexTimeout + ")";
        }
    }

    /**
     * Creates an instance of Neo4jEmbeddingStore
     *
     * @param driver:            the {@link Driver} (required)
     * @param dimension:         the dimension (required)
     * @param config:            the {@link SessionConfig}  (optional, default is `SessionConfig.forDatabase(`databaseName`)`)
     * @param label:             the optional label name (default: "Document")
     * @param embeddingProperty: the optional embeddingProperty name (default: "embedding")
     * @param idProperty:        the optional id property name (default: "id")
     * @param metadataPrefix:    the optional metadata prefix (default: "")
     * @param textProperty:      the optional textProperty property name (default: "text")
     * @param indexName:         the optional index name (default: "vector")
     * @param databaseName:      the optional database name (default: "neo4j")
     * @param retrievalQuery:    the optional retrieval query
     *                           (default: "RETURN properties(node) AS metadata, node.`idProperty` AS `idProperty`, node.`textProperty` AS `textProperty`, node.`embeddingProperty` AS `embeddingProperty`, score")
     */
    public Neo4jEmbeddingStore(
            SessionConfig config,
            Driver driver,
            int dimension,
            String label,
            String embeddingProperty,
            String idProperty,
            String metadataPrefix,
            String textProperty,
            String indexName,
            String databaseName,
            String retrievalQuery,
            long awaitIndexTimeout) {

        /* required configs */
        this.driver = ensureNotNull(driver, "driver");
        this.dimension = ensureBetween(dimension, 0, 4096, "dimension");

        /* optional configs */
        this.databaseName = getOrDefault(databaseName, DEFAULT_DATABASE_NAME);
        this.config = getOrDefault(config, SessionConfig.forDatabase(this.databaseName));
        this.label = getOrDefault(label, DEFAULT_LABEL);
        this.embeddingProperty = getOrDefault(embeddingProperty, DEFAULT_EMBEDDING_PROP);
        this.idProperty = getOrDefault(idProperty, DEFAULT_ID_PROP);
        this.indexName = getOrDefault(indexName, DEFAULT_IDX_NAME);
        this.metadataPrefix = getOrDefault(metadataPrefix, "");
        this.textProperty = getOrDefault(textProperty, DEFAULT_TEXT_PROP);
        this.awaitIndexTimeout = getOrDefault(awaitIndexTimeout, DEFAULT_AWAIT_INDEX_TIMEOUT);

        /* sanitize labels and property names, to prevent from Cypher Injections */
        this.sanitizedLabel = sanitizeOrThrows(this.label, "label");
        this.sanitizedEmbeddingProperty = sanitizeOrThrows(this.embeddingProperty, "embeddingProperty");
        this.sanitizedIdProperty = sanitizeOrThrows(this.idProperty, "idProperty");
        this.sanitizedText = sanitizeOrThrows(this.textProperty, "textProperty");

        /* retrieval query: must necessarily return the following column:
            `metadata`,
            `score`,
            `this.idProperty (default "id")`,
            `this.textProperty (default "textProperty")`,
            `this.embeddingProperty (default "embedding")`
        */
        String defaultRetrievalQuery = String.format(
                "RETURN properties(node) AS metadata, node.%1$s AS %1$s, node.%2$s AS %2$s, node.%3$s AS %3$s, score",
                this.sanitizedIdProperty, this.sanitizedText, this.sanitizedEmbeddingProperty
        );
        this.retrievalQuery = getOrDefault(retrievalQuery, defaultRetrievalQuery);

        this.notMetaKeys = new HashSet<>(Arrays.asList(this.idProperty, this.embeddingProperty, this.textProperty));

        /* auto-schema creation */
        createSchema();
    }

    /*
    Methods with `@Override`
    */

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {

        var embeddingValue = Values.value(request.queryEmbedding().vector());

        try (var session = session()) {
            Map<String, Object> params = Map.of("indexName", indexName,
                    "embeddingValue", embeddingValue,
                    "minScore", request.minScore(),
                    "maxResults", request.maxResults());

            List<EmbeddingMatch<TextSegment>> matches = session
                    .run("""
                                    CALL db.index.vector.queryNodes($indexName, $maxResults, $embeddingValue)
                                    YIELD node, score
                                    WHERE score >= $minScore
                                    """ + retrievalQuery,
                            params)
                    .list(item -> toEmbeddingMatch(this, item));

            return new EmbeddingSearchResult<>(matches);
        }
    }

    /*
    Private methods
    */

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("[do not add empty embeddings to neo4j]");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        bulk(ids, embeddings, embedded);
    }

    private void bulk(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        Stream<List<Map<String, Object>>> rowsBatched = getRowsBatched(this, ids, embeddings, embedded);

        try (var session = session()) {
            rowsBatched.forEach(rows -> {
                String statement = """
                        UNWIND $rows AS row
                        MERGE (u:%1$s {%2$s: row.%2$s})
                        SET u += row.%3$s
                        WITH row, u
                        CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                        RETURN count(*)""".formatted(
                        this.sanitizedLabel,
                        this.sanitizedIdProperty,
                        PROPS,
                        EMBEDDINGS_ROW_KEY);

                Map<String, Object> params = Map.of(
                        "rows", rows,
                        "embeddingProperty", this.embeddingProperty
                );

                session.executeWrite(tx -> tx.run(statement, params).consume());
            });
        }
    }

    private void createSchema() {
        if (!indexExists()) {
            createIndex();
        }
        createUniqueConstraint();
    }

    private void createUniqueConstraint() {
        try (var session = session()) {
            String query = String.format(
                    "CREATE CONSTRAINT IF NOT EXISTS FOR (n:%s) REQUIRE n.%s IS UNIQUE",
                    this.sanitizedLabel,
                    this.sanitizedIdProperty
            );
            session.run(query);
        }
    }

    private boolean indexExists() {
        try (var session = session()) {
            Map<String, Object> params = Map.of("name", this.indexName);
            var resIndex = session.run("SHOW INDEX WHERE type = 'VECTOR' AND name = $name", params);
            if (!resIndex.hasNext()) {
                return false;
            }
            var record = resIndex.single();
            List<String> idxLabels = record
                    .get("labelsOrTypes")
                    .asList(Value::asString);
            List<Object> idxProps = record.get("properties").asList();

            boolean isIndexDifferent = !idxLabels.equals(singletonList(this.label))
                    || !idxProps.equals(singletonList(this.embeddingProperty));
            if (isIndexDifferent) {
                String errMessage = String.format("""
                                It's not possible to create an index for the label `%s` and the property `%s`,
                                as there is another index with name `%s` with different labels: `%s` and properties `%s`.
                                Please provide another indexName to create the vector index, or delete the existing one""",
                        this.label,
                        this.embeddingProperty,
                        this.indexName,
                        idxLabels,
                        idxProps);
                throw new RuntimeException(errMessage);
            }
            return true;
        }
    }

    private void createIndex() {
        Map<String, Object> params = Map.of("indexName", this.indexName,
                "label", this.label,
                "embeddingProperty", this.embeddingProperty,
                "dimension", this.dimension);

        // create vector index
        try (var session = session()) {
            session.run("CALL db.index.vector.createNodeIndex($indexName, $label, $embeddingProperty, $dimension, 'cosine')",
                    params);

            session.run("CALL db.awaitIndexes($timeout)",
                    Map.of("timeout", awaitIndexTimeout)
            ).consume();
        }
    }

    private Session session() {
        return this.driver.session(this.config);
    }
}
