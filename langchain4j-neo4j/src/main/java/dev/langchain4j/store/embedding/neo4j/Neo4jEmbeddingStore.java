package dev.langchain4j.store.embedding.neo4j;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;
import lombok.Getter;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.IntStream;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isCollectionEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.Collections.singletonList;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;

import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.*;

/**
 * Represents a Vector index as an embedding store.
 * Annotated with `@Getter` to be used in {@link Neo4jEmbeddingUtils}
 */
@Getter
public class Neo4jEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(Neo4jEmbeddingStore.class);

    /* Neo4j Java Driver settings */
    private final Driver driver;
    private final SessionConfig config;

    /* Neo4j schema field settings */
    private final int dimension;
    private final Neo4jDistanceType distanceType;

    private final String indexName;
    private final String metadataPrefix;
    private final String embeddingProperty;
    private final String label;
    private final String text;

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
     * @param config: the {@link SessionConfig}  (optional, default is `SessionConfig.forDatabase("neo4j")`)
     * @param label: the optional label name (default: "Document")
     * @param property: the optional property name (default: "embedding")
     * @param distanceType: the optional distanceType (default: "cosine")
     * @param metadataPrefix: the optional metadata prefix (default: "")
     * @param text: the optional text property name (default: "text")
     * @param indexName: the optional index name (default: "langchain-embedding-index")
     */
    @Builder
    public Neo4jEmbeddingStore(
            SessionConfig config,
            Driver driver,
            int dimension,
            String label,
            String property,
            Neo4jDistanceType distanceType,
            String metadataPrefix,
            String text,
            String indexName) {
        
        /* required configs */
        ensureNotNull(driver, "driver");
        ensureNotNull(dimension, "dimension");

        this.driver = driver;
        this.dimension = dimension;
        
        /* optional configs */
        this.config = getOrDefault(config, SessionConfig.forDatabase("neo4j"));
        this.label = getOrDefault(label, DEFAULT_LABEL);
        this.embeddingProperty = getOrDefault(property, DEFAULT_EMBEDDING_PROP);
        this.distanceType = getOrDefault(distanceType, Neo4jDistanceType.COSINE);
        this.indexName = getOrDefault(indexName, DEFAULT_IDX_NAME);
        this.metadataPrefix = getOrDefault(metadataPrefix, "");
        this.text = getOrDefault(text, DEFAULT_TEXT_PROP);
        
        /* auto-index creation */
        createIndexIfNotExist();
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
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .toList();
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        var embeddingValue = Values.value(referenceEmbedding.vector());
        
        try (var session = session()) {
            return session
                    .run("""
						CALL db.index.vector.queryNodes($indexName, $maxResults, $embeddingValue)
						YIELD node, score
						WHERE score >= $minScore
						RETURN node, score
						""", Map.of("indexName", indexName,
                            "embeddingValue", embeddingValue,
                            "minScore", minScore,
                            "maxResults", maxResults))
                    .list(item -> Neo4jEmbeddingUtils.toEmbeddingMatch(this, item));
        }
    }


    /*
    Private methods
    */
    
    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isCollectionEmpty(ids) || isCollectionEmpty(embeddings)) {
            log.info("[do not add empty embeddings to neo4j]");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        bulk(ids, embeddings, embedded);
    }

    private void bulk(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {

        List<Map<String, Object>> rows = IntStream.range(0, ids.size())
                .mapToObj(idx -> toRecord(this, idx, ids, embeddings, embedded))
                .toList();

        try (var session = session()) {
            String statement = """
                            UNWIND $rows AS row
                            MERGE (u:%1$s {%2$s: row.%2$s})
                            SET u += row.%3$s
                            WITH row, u
                            CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                            RETURN *""".formatted(
                    this.label,
                    ID_ROW_KEY,
                    PROPS,
                    EMBEDDINGS_ROW_KEY);

            Map<String, Object> params = Map.of(
                    "rows", rows,
                    "embeddingProperty", this.embeddingProperty
            );
            
            session.run(statement, params).consume();
        }
    }

    private void createIndexIfNotExist() {
        if (!indexExists()) {
            createIndex();
        }
    }

    private boolean indexExists() {
        try (var session = session()) {
            Map<String, Object> params = Map.of("name", this.indexName);
            return session.run("SHOW INDEX WHERE type = 'VECTOR' AND name = $name", params)
                    .hasNext();
        }
    }

    private void createIndex() {
        Map<String, Object> params = Map.of("indexName", this.indexName,
                "label", this.label,
                "embeddingProperty", this.embeddingProperty,
                "dimension", this.dimension,
                "distanceType", this.distanceType.getValue());

        // create vector index
        try (var session = session()) {
            session.run("CALL db.index.vector.createNodeIndex($indexName, $label, $embeddingProperty, $dimension, $distanceType)",
                    params);

            session.run("CALL db.awaitIndexes()").consume();
        }
    }

    private Session session() {
        return this.driver.session(this.config);
    }
}
