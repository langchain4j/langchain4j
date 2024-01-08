package dev.langchain4j.store.embedding.redis;

import com.google.gson.Gson;
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
import redis.clients.jedis.search.*;

import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.store.embedding.redis.RedisSchema.SCORE_FIELD_NAME;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static redis.clients.jedis.search.IndexDefinition.Type.JSON;
import static redis.clients.jedis.search.RediSearchUtil.ToByteArray;

/**
 * Represents a <a href="https://redis.io/">Redis</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 */
public class RedisEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(RedisEmbeddingStore.class);
    private static final Gson GSON = new Gson();

    private final JedisPooled client;
    private final RedisSchema schema;

    /**
     * Creates an instance of RedisEmbeddingStore
     *
     * @param host               Redis Stack Server host
     * @param port               Redis Stack Server port
     * @param user               Redis Stack username (optional)
     * @param password           Redis Stack password (optional)
     * @param indexName          The name of the index (optional). Default value: "embedding-index".
     * @param dimension          Embedding vector dimension
     * @param metadataFieldsName Metadata fields name (optional)
     */
    public RedisEmbeddingStore(String host,
                               Integer port,
                               String user,
                               String password,
                               String indexName,
                               Integer dimension,
                               List<String> metadataFieldsName) {
        ensureNotBlank(host, "host");
        ensureNotNull(port, "port");
        ensureNotNull(dimension, "dimension");

        this.client = user == null ? new JedisPooled(host, port) : new JedisPooled(host, port, user, password);
        this.schema = RedisSchema.builder()
                .indexName(getOrDefault(indexName, "embedding-index"))
                .dimension(dimension)
                .metadataFieldsName(metadataFieldsName)
                .build();

        if (!isIndexExist(schema.getIndexName())) {
            createIndex(schema.getIndexName());
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
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        // Using KNN query on @vector field
        String queryTemplate = "*=>[ KNN %d @%s $BLOB AS %s ]";
        List<String> returnFields = new ArrayList<>(schema.getMetadataFieldsName());
        returnFields.addAll(asList(schema.getVectorFieldName(), schema.getScalarFieldName(), SCORE_FIELD_NAME));
        Query query = new Query(format(queryTemplate, maxResults, schema.getVectorFieldName(), SCORE_FIELD_NAME))
                .addParam("BLOB", ToByteArray(referenceEmbedding.vector()))
                .returnFields(returnFields.toArray(new String[0]))
                .setSortBy(SCORE_FIELD_NAME, true)
                .dialect(2);

        SearchResult result = client.ftSearch(schema.getIndexName(), query);
        List<Document> documents = result.getDocuments();

        return toEmbeddingMatch(documents, minScore);
    }

    private void createIndex(String indexName) {
        IndexDefinition indexDefinition = new IndexDefinition(JSON);
        indexDefinition.setPrefixes(schema.getPrefix());
        String res = client.ftCreate(indexName, FTCreateParams.createParams()
                .on(IndexDataType.JSON)
                .addPrefix(schema.getPrefix()), schema.toSchemaFields());
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
                fields.put(schema.getVectorFieldName(), embedding.vector());
                if (textSegment != null) {
                    // do not check metadata key is included in RedisSchema#metadataFieldsName
                    fields.put(schema.getScalarFieldName(), textSegment.text());
                    fields.putAll(textSegment.metadata().asMap());
                }
                String key = schema.getPrefix() + id;
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
                    String id = document.getId().substring(schema.getPrefix().length());
                    String text = document.hasProperty(schema.getScalarFieldName()) ? document.getString(schema.getScalarFieldName()) : null;
                    TextSegment embedded = null;
                    if (text != null) {
                        List<String> metadataFieldsName = schema.getMetadataFieldsName();
                        Map<String, String> metadata = metadataFieldsName.stream()
                                .filter(document::hasProperty)
                                .collect(Collectors.toMap(metadataFieldName -> metadataFieldName, document::getString));
                        embedded = new TextSegment(text, new Metadata(metadata));
                    }
                    Embedding embedding = new Embedding(GSON.fromJson(document.getString(schema.getVectorFieldName()), float[].class));
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
        private Integer dimension;
        private List<String> metadataFieldsName = new ArrayList<>();

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
         * @param dimension embedding vector dimension
         * @return builder
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param metadataFieldsName metadata fields name (optional)
         */
        public Builder metadataFieldsName(List<String> metadataFieldsName) {
            this.metadataFieldsName = metadataFieldsName;
            return this;
        }

        public RedisEmbeddingStore build() {
            return new RedisEmbeddingStore(host, port, user, password, indexName, dimension, metadataFieldsName);
        }
    }
}
