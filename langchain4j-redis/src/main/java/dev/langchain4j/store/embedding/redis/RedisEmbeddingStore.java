package dev.langchain4j.store.embedding.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static dev.langchain4j.store.embedding.redis.RedisSchema.SCORE_FIELD_NAME;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static redis.clients.jedis.search.RediSearchUtil.ToByteArray;

/**
 * Represents a <a href="https://redis.io/">Redis</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 */
public class RedisEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(RedisEmbeddingStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JedisPooled client;
    private final RedisSchema schema;

    /**
     * Creates an instance of RedisEmbeddingStore
     *
     * @param host         Redis Stack Server host
     * @param port         Redis Stack Server port
     * @param user         Redis Stack username (optional)
     * @param password     Redis Stack password (optional)
     * @param indexName    The name of the index (optional). Default value: "embedding-index".
     * @param prefix       The prefix of the key, should end with a colon (e.g., "embedding:") (optional). Default value: "embedding:".
     * @param dimension    Embedding vector dimension
     * @param metadataKeys Metadata keys that should be persisted (optional)
     */
    public RedisEmbeddingStore(String host,
                               Integer port,
                               String user,
                               String password,
                               String indexName,
                               String prefix,
                               Integer dimension,
                               Collection<String> metadataKeys) {
        ensureNotBlank(host, "host");
        ensureNotNull(port, "port");
        ensureNotNull(dimension, "dimension");

        this.client = user == null ? new JedisPooled(host, port) : new JedisPooled(host, port, user, password);
        this.schema = RedisSchema.builder()
            .indexName(getOrDefault(indexName, "embedding-index"))
            .prefix(getOrDefault(prefix, "embedding:"))
            .dimension(dimension)
            .metadataKeys(metadataKeys)
            .build();

        if (!isIndexExist(schema.indexName())) {
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
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        // Using KNN query on @vector field
        String queryTemplate = "*=>[ KNN %d @%s $BLOB AS %s ]";
        List<String> returnFields = new ArrayList<>(schema.metadataKeys());
        returnFields.addAll(asList(schema.vectorFieldName(), schema.scalarFieldName(), SCORE_FIELD_NAME));
        Query query = new Query(format(queryTemplate, maxResults, schema.vectorFieldName(), SCORE_FIELD_NAME))
            .addParam("BLOB", ToByteArray(referenceEmbedding.vector()))
            .returnFields(returnFields.toArray(new String[0]))
            .setSortBy(SCORE_FIELD_NAME, true)
            .dialect(2);

        SearchResult result = client.ftSearch(schema.indexName(), query);
        List<Document> documents = result.getDocuments();

        return toEmbeddingMatch(documents, minScore);
    }

    private void createIndex(String indexName) {
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
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
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
                    // do not check metadata key is included in RedisSchema#metadataKeys
                    fields.put(schema.scalarFieldName(), textSegment.text());
                    fields.putAll(textSegment.metadata().asMap());
                }
                String key = schema.prefix() + id;
                pipeline.jsonSetWithEscape(key, Path2.of("$"), fields);
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

    private List<EmbeddingMatch<TextSegment>> toEmbeddingMatch(List<Document> documents, double minScore) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        return documents.stream()
            .map(document -> {
                double score = (2 - Double.parseDouble(document.getString(SCORE_FIELD_NAME))) / 2;
                String id = document.getId().substring(schema.prefix().length());
                String text = document.hasProperty(schema.scalarFieldName()) ? document.getString(schema.scalarFieldName()) : null;
                TextSegment embedded = null;
                if (text != null) {
                    Map<String, String> metadata = schema.metadataKeys().stream()
                        .filter(document::hasProperty)
                        .collect(toMap(metadataKey -> metadataKey, document::getString));
                    embedded = new TextSegment(text, new Metadata(metadata));
                }
                Embedding embedding;
                try {
                    float[] vectors = OBJECT_MAPPER.readValue(document.getString(schema.vectorFieldName()), float[].class);
                    embedding = new Embedding(vectors);
                } catch (JsonProcessingException e) {
                    throw new RedisRequestFailedException("failed to parse embedding", e);
                }
                return new EmbeddingMatch<>(score, id, embedding, embedded);
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
        private String prefix;
        private Integer dimension;
        private Collection<String> metadataKeys = new ArrayList<>();

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
         * @param prefix The prefix of the key, should end with a colon (e.g., "embedding:") (optional). Default value: "embedding:".
         * @return builder
         */
        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * @param dimension embedding vector dimension
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
            this.metadataKeys = metadataFieldsName;
            return this;
        }

        /**
         * @param metadataKeys Metadata keys that should be persisted (optional)
         */
        public Builder metadataKeys(Collection<String> metadataKeys) {
            this.metadataKeys = metadataKeys;
            return this;
        }

        public RedisEmbeddingStore build() {
            return new RedisEmbeddingStore(host, port, user, password, indexName, prefix, dimension, metadataKeys);
        }
    }
}
