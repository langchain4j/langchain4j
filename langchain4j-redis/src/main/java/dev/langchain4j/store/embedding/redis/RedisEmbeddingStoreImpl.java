package dev.langchain4j.store.embedding.redis;

import com.google.gson.Gson;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.ValidationUtils;
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

import static dev.langchain4j.internal.Utils.isCollectionEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static redis.clients.jedis.search.RediSearchUtil.ToByteArray;

/**
 * Redis Embedding Store Implementation
 */
public class RedisEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(RedisEmbeddingStoreImpl.class);
    private static final Gson GSON = new Gson();

    private final JedisPooled client;
    private final RedisSchema schema;

    public RedisEmbeddingStoreImpl(String host,
                                   Integer port,
                                   String user,
                                   String password,
                                   Integer dimension,
                                   List<String> metadataFieldsName) {
        host = ensureNotNull(host, "url");
        ensureNotNull(port, "port");
        ensureNotNull(dimension, "dimension");

        client = user == null ? new JedisPooled(host, port) : new JedisPooled(host, port, user, password);
        schema = RedisSchema.builder()
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
                .collect(Collectors.toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(Collectors.toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        // Using KNN query on @vector field
        String queryTemplate = "*=>[ KNN %d @%s $BLOB AS %s ]";
        List<String> returnFields = new ArrayList<>(schema.getMetadataFieldsName());
        returnFields.addAll(Arrays.asList(schema.getVectorFieldName(), schema.getScalarFieldName(), RedisSchema.SCORE_FIELD_NAME));
        Query query = new Query(String.format(queryTemplate, maxResults, schema.getVectorFieldName(), RedisSchema.SCORE_FIELD_NAME))
                .addParam("BLOB", ToByteArray(referenceEmbedding.vector()))
                .returnFields(returnFields.toArray(new String[0]))
                .setSortBy(RedisSchema.SCORE_FIELD_NAME, true)
                .dialect(2);

        SearchResult result = client.ftSearch(schema.getIndexName(), query);
        List<Document> documents = result.getDocuments();

        return toEmbeddingMatch(documents, minScore);
    }

    private void createIndex(String indexName) {
        IndexDefinition indexDefinition = new IndexDefinition(IndexDefinition.Type.JSON);
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
        Set<String> indexSets = client.ftList();
        return indexSets.contains(indexName);
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(Collections.singletonList(id), Collections.singletonList(embedding), embedded == null ? null : Collections.singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isCollectionEmpty(ids) || isCollectionEmpty(embeddings)) {
            log.info("do not add empty embeddings to redis");
            return;
        }
        ValidationUtils.ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ValidationUtils.ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        Pipeline pipeline = client.pipelined();

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
        List<Object> responses = pipeline.syncAndReturnAll();
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

        return documents.stream().map(document -> {
            double score = (2 - Double.parseDouble(document.getString(RedisSchema.SCORE_FIELD_NAME))) / 2;
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
        }).filter(embeddingMatch -> embeddingMatch.score() >= minScore).collect(Collectors.toList());
    }
}
