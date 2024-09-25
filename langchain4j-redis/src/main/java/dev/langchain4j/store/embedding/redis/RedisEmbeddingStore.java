package dev.langchain4j.store.embedding.redis;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.search.*;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;

import java.util.*;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.store.embedding.redis.RedisJsonUtils.toProperties;
import static dev.langchain4j.store.embedding.redis.RedisSchema.*;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static redis.clients.jedis.search.IndexDefinition.Type.JSON;
import static redis.clients.jedis.search.RediSearchUtil.toByteArray;

/**
 * Represents a <a href="https://redis.io/">Redis</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 */
public class RedisEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(RedisEmbeddingStore.class);

    private static final String QUERY_TEMPLATE = "%s=>[ KNN %d @%s $BLOB AS %s ]";

    private final JedisPooled client;
    private final RedisSchema schema;
    private final RedisMetadataFilterMapper filterMapper;

    /**
     * Creates an instance of RedisEmbeddingStore
     *
     * @param host           Redis Stack Server host
     * @param port           Redis Stack Server port
     * @param user           Redis Stack username (optional)
     * @param password       Redis Stack password (optional)
     * @param indexName      The name of the index (optional). Default value: "embedding-index".
     * @param dimension      Embedding vector dimension
     * @param schemaFieldMap Metadata schemaField that should be persisted (optional)
     */
    public RedisEmbeddingStore(String host,
                               Integer port,
                               String user,
                               String password,
                               String indexName,
                               Integer dimension,
                               Map<String, SchemaField> schemaFieldMap) {
        ensureNotBlank(host, "host");
        ensureNotNull(port, "port");

        this.client = user == null ? new JedisPooled(host, port) : new JedisPooled(host, port, user, password);
        this.schema = RedisSchema.builder()
                .indexName(getOrDefault(indexName, "embedding-index"))
                .dimension(dimension)
                .schemaFieldMap(copyIfNotNull(schemaFieldMap))
                .build();
        this.filterMapper = new RedisMetadataFilterMapper(schemaFieldMap);

        if (!isIndexExist(schema.indexName())) {
            ensureNotNull(dimension, "dimension");
            createIndex(schema.indexName());
        }
    }

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
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        // Using KNN query on @vector field
        Query query = new Query(format(QUERY_TEMPLATE, filterMapper.mapToFilter(request.filter()), request.maxResults(), schema.vectorFieldName(), SCORE_FIELD_NAME))
                .addParam("BLOB", toByteArray(request.queryEmbedding().vector()))
                .setSortBy(SCORE_FIELD_NAME, true)
                .dialect(2);

        SearchResult result = client.ftSearch(schema.indexName(), query);
        List<Document> documents = result.getDocuments();

        return new EmbeddingSearchResult<>(toEmbeddingMatch(documents, request.minScore()));
    }

    private void createIndex(String indexName) {
        IndexDefinition indexDefinition = new IndexDefinition(JSON);
        indexDefinition.setPrefixes(schema.prefix());
        String res = client.ftCreate(indexName, FTCreateParams.createParams()
                .on(IndexDataType.JSON)
                .addPrefix(schema.prefix()), schema.toSchemaFields());
        if (!"OK".equals(res)) {
            if (log.isErrorEnabled()) {
                log.error("create index error, msg={}", res);
            }
            throw new RedisRequestFailedException("create index error, msg=" + res);
        }
    }

    private boolean isIndexExist(String indexName) {
        Set<String> indexes = client.ftList();
        return indexes.contains(indexName);
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("do not add empty embeddings to redis");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        List<Object> responses;
        try (Pipeline pipeline = client.pipelined()) {

            int size = ids.size();
            for (int i = 0; i < size; i++) {
                String id = ids.get(i);
                Embedding embedding = embeddings.get(i);
                TextSegment textSegment = embedded == null ? null : embedded.get(i);
                Map<String, Object> fields = new HashMap<>();
                fields.put(schema.vectorFieldName(), embedding.vector());
                if (textSegment != null) {
                    fields.put(schema.scalarFieldName(), textSegment.text());
                    fields.putAll(textSegment.metadata().toMap());
                }
                String key = schema.prefix() + id;
                pipeline.jsonSetWithEscape(key, JSON_SET_PATH, fields);
            }

            responses = pipeline.syncAndReturnAll();
        }

        Optional<Object> errResponse = responses.stream().filter(response -> !"OK".equals(response)).findAny();
        if (errResponse.isPresent()) {
            if (log.isErrorEnabled()) {
                log.error("add embedding failed, msg={}", errResponse.get());
            }
            throw new RedisRequestFailedException("add embedding failed, msg=" + errResponse.get());
        }
    }

    @SuppressWarnings("unchecked")
    private List<EmbeddingMatch<TextSegment>> toEmbeddingMatch(List<Document> documents, double minScore) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        return documents.stream()
                .map(document -> {
                    double score = (2 - Double.parseDouble(document.getString(SCORE_FIELD_NAME))) / 2;
                    String id = document.getId().substring(schema.prefix().length());

                    Map<String, Object> properties = toProperties(document.getString(JSON_KEY));

                    List<Double> vectors = (List<Double>) properties.get(schema.vectorFieldName());
                    Embedding embedding = Embedding.from(
                            vectors.stream()
                                    .map(Double::floatValue)
                                    .collect(toList())
                    );

                    String text = properties.containsKey(schema.scalarFieldName()) ? (String) properties.get(schema.scalarFieldName()) : null;
                    TextSegment textSegment = null;
                    if (text != null) {
                        Map<String, Object> metadata = schema.schemaFieldMap().keySet().stream()
                                .filter(properties::containsKey)
                                .collect(toMap(metadataKey -> metadataKey, properties::get));
                        textSegment = TextSegment.from(text, Metadata.from(metadata));
                    }

                    return new EmbeddingMatch<>(score, id, embedding, textSegment);
                })
                .filter(embeddingMatch -> embeddingMatch.score() >= minScore)
                .collect(toList());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String host;
        private Integer port;
        private String user;
        private String password;
        private String indexName;
        private Integer dimension;
        private Map<String, SchemaField> schemaFieldMap = new HashMap<>();

        /**
         * @param host Redis Stack host
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * @param port Redis Stack port
         */
        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        /**
         * @param user Redis Stack username (optional)
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * @param password Redis Stack password (optional)
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * @param indexName The name of the index (optional). Default value: "embedding-index".
         * @return builder
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * @param dimension embedding vector dimension (optional)
         * @return builder
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param metadataFieldsName metadata fields names (optional)
         * @deprecated use {@link #metadataKeys(Collection)} instead
         */
        @Deprecated
        public Builder metadataFieldsName(Collection<String> metadataFieldsName) {
            return metadataKeys(metadataFieldsName);
        }

        /**
         * @param metadataKeys Metadata keys that should be persisted (optional)
         * @deprecated use {@link #schemaFiledMap(Map)}} instead
         */
        @Deprecated
        public Builder metadataKeys(Collection<String> metadataKeys) {
            metadataKeys.forEach(metadataKey -> schemaFieldMap.put(metadataKey, TextField.of(JSON_PATH_PREFIX + metadataKey).as(metadataKey).weight(1.0)));
            return this;
        }

        /**
         * @param schemaFieldMap Metadata schemaField that should be persisted (optional)
         */
        public Builder schemaFiledMap(Map<String, SchemaField> schemaFieldMap) {
            this.schemaFieldMap = schemaFieldMap;
            return this;
        }

        public RedisEmbeddingStore build() {
            return new RedisEmbeddingStore(host, port, user, password, indexName, dimension, schemaFieldMap);
        }
    }
}
