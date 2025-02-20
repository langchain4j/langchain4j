package dev.langchain4j.store.embedding.neo4j;

import static dev.langchain4j.Neo4jUtils.sanitizeOrThrows;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.*;
import static java.util.Collections.singletonList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.graph.neo4j.BaseNeo4jBuilder;
import java.util.*;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Getter;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a Vector index as an embedding store.
 * Annotated with `@Getter` to be used in {@link Neo4jEmbeddingUtils}
 */
@Getter
public class Neo4jEmbeddingStore extends BaseNeo4jBuilder implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(Neo4jEmbeddingStore.class);

    /* Neo4j schema field settings */
    private final int dimension;
    private final long awaitIndexTimeout;

    private final String indexName;
    private final String metadataPrefix;
    private final String embeddingProperty;
    private final String sanitizedEmbeddingProperty;
    private final String retrievalQuery;
    private final Set<String> notMetaKeys;

    /**
     * Creates an instance of Neo4jEmbeddingStore defining a {@link Driver}
     * starting from uri, user and password
     */
    public static class Neo4jEmbeddingStoreBuilder {
        public Neo4jEmbeddingStoreBuilder withBasicAuth(String uri, String user, String password) {
            return this.driver(GraphDatabase.driver(uri, AuthTokens.basic(user, password)));
        }
    }

    /**
     * Creates an instance of Neo4jEmbeddingStore
     * @param driver: the {@link Driver} (required)
     * @param dimension: the dimension (required)
     * @param config: the {@link SessionConfig}  (optional, default is `SessionConfig.forDatabase(`databaseName`)`)
     * @param label: the optional label name (default: "Document")
     * @param embeddingProperty: the optional embeddingProperty name (default: "embedding")
     * @param idProperty: the optional id property name (default: "id")
     * @param metadataPrefix: the optional metadata prefix (default: "")
     * @param textProperty: the optional textProperty property name (default: "text")
     * @param indexName: the optional index name (default: "vector")
     * @param databaseName: the optional database name (default: "neo4j")
     * @param retrievalQuery: the optional retrieval query
     *                        (default: "RETURN properties(node) AS metadata, node.`idProperty` AS `idProperty`, node.`textProperty` AS `textProperty`, node.`embeddingProperty` AS `embeddingProperty`, score")
     */
    @Builder
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
        super(config, databaseName, driver, label, idProperty, textProperty);

        /* required configs */
        this.dimension = ensureBetween(dimension, 0, 4096, "dimension");

        /* optional configs */
        this.embeddingProperty = getOrDefault(embeddingProperty, DEFAULT_EMBEDDING_PROP);
        this.indexName = getOrDefault(indexName, DEFAULT_IDX_NAME);
        this.metadataPrefix = getOrDefault(metadataPrefix, "");
        this.awaitIndexTimeout = getOrDefault(awaitIndexTimeout, DEFAULT_AWAIT_INDEX_TIMEOUT);

        /* sanitize labels and property names, to prevent from Cypher Injections */
        this.sanitizedEmbeddingProperty = sanitizeOrThrows(this.embeddingProperty, "embeddingProperty");

        /* retrieval query: must necessarily return the following column:
            `metadata`,
            `score`,
            `this.idProperty (default "id")`,
            `this.textProperty (default "textProperty")`,
            `this.embeddingProperty (default "embedding")`
        */
        String defaultRetrievalQuery = String.format(
                "RETURN properties(node) AS metadata, node.%1$s AS %1$s, node.%2$s AS %2$s, node.%3$s AS %3$s, score",
                this.sanitizedIdProperty, this.sanitizedTextProperty, this.sanitizedEmbeddingProperty);
        this.retrievalQuery = getOrDefault(retrievalQuery, defaultRetrievalQuery);

        this.notMetaKeys = new HashSet<>(Arrays.asList(this.idProperty, this.embeddingProperty, this.textProperty));

        /* auto-schema creation */
        createSchema();
    }

    @Override
    protected String getDefaultLabel() {
        return DEFAULT_LABEL;
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
            Map<String, Object> params = Map.of(
                    "indexName",
                    indexName,
                    "embeddingValue",
                    embeddingValue,
                    "minScore",
                    request.minScore(),
                    "maxResults",
                    request.maxResults());

            List<EmbeddingMatch<TextSegment>> matches = session.run(
                            """
                        CALL db.index.vector.queryNodes($indexName, $maxResults, $embeddingValue)
                        YIELD node, score
                        WHERE score >= $minScore
                        """
                                    + retrievalQuery,
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
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        bulk(ids, embeddings, embedded);
    }

    private void bulk(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        Stream<List<Map<String, Object>>> rowsBatched = getRowsBatched(this, ids, embeddings, embedded);

        try (var session = session()) {
            rowsBatched.forEach(rows -> {
                String statement =
                        """
                                UNWIND $rows AS row
                                MERGE (u:%1$s {%2$s: row.%2$s})
                                SET u += row.%3$s
                                WITH row, u
                                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                                RETURN count(*)"""
                                .formatted(this.sanitizedLabel, this.sanitizedIdProperty, PROPS, EMBEDDINGS_ROW_KEY);

                Map<String, Object> params = Map.of("rows", rows, "embeddingProperty", this.embeddingProperty);

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
                    this.sanitizedLabel, this.sanitizedIdProperty);
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
            List<String> idxLabels = record.get("labelsOrTypes").asList(Value::asString);
            List<Object> idxProps = record.get("properties").asList();

            boolean isIndexDifferent = !idxLabels.equals(singletonList(this.label))
                    || !idxProps.equals(singletonList(this.embeddingProperty));
            if (isIndexDifferent) {
                String errMessage = String.format(
                        """
                                It's not possible to create an index for the label `%s` and the property `%s`,
                                as there is another index with name `%s` with different labels: `%s` and properties `%s`.
                                Please provide another indexName to create the vector index, or delete the existing one""",
                        this.label, this.embeddingProperty, this.indexName, idxLabels, idxProps);
                throw new RuntimeException(errMessage);
            }
            return true;
        }
    }

    private void createIndex() {
        Map<String, Object> params = Map.of(
                "indexName",
                this.indexName,
                "label",
                this.label,
                "embeddingProperty",
                this.embeddingProperty,
                "dimension",
                this.dimension);

        // create vector index
        try (var session = session()) {
            session.run(
                    "CALL db.index.vector.createNodeIndex($indexName, $label, $embeddingProperty, $dimension, 'cosine')",
                    params);

            session.run("CALL db.awaitIndexes($timeout)", Map.of("timeout", awaitIndexTimeout))
                    .consume();
        }
    }
}
