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
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.isCollectionEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static redis.clients.jedis.search.RediSearchUtil.ToByteArray;

/**
 * Redis Embedding Store Implementation
 */
public class RedisEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(RedisEmbeddingStoreImpl.class);
    private static final Gson GSON = new Gson();

    private final JedisPooled client;
    private final RedisSchema schema;

    public RedisEmbeddingStoreImpl(String url, Integer dimension) {
        url = ensureNotNull(url, "url");
        dimension = ensureNotNull(dimension, "dimension");

        client = new JedisPooled(url);
        schema = RedisSchema.builder().dimension(dimension).build();

        createIndex(schema.getIndexName());
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
        Query query = new Query(String.format(queryTemplate, maxResults, schema.getVectorFieldName(), RedisSchema.SCORE_FIELD_NAME))
                .addParam("BLOB", ToByteArray(referenceEmbedding.vector()))
                .returnFields(schema.getVectorFieldName(), schema.getScalarFieldName(), RedisSchema.SCORE_FIELD_NAME, schema.getMetadataFieldName())
                .setSortBy(RedisSchema.SCORE_FIELD_NAME, true)
                .dialect(2);

        SearchResult result = client.ftSearch(schema.getIndexName(), query);
        List<Document> documents = result.getDocuments();

        return toEmbeddingMatch(documents);
    }

    private void createIndex(String indexName) {
        if (!isIndexExist(indexName)) {
            IndexDefinition indexDefinition = new IndexDefinition(IndexDefinition.Type.HASH);
            indexDefinition.setPrefixes(schema.getPrefix());
            String res = client.ftCreate(indexName, IndexOptions.defaultOptions()
                    .setDefinition(indexDefinition), schema.toSchema());
            if (!"OK".equals(res)) {
                throw new JedisDataException("create index error, msg=" + res);
            }
        }
    }

    private boolean isIndexExist(String indexName) {
        // redis do not contain command like ftExists
        Set<String> indexSets = client.ftList();
        return indexSets.contains(indexName);
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(Collections.singletonList(id), Collections.singletonList(embedding), embedded == null ? null : Collections.singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isCollectionEmpty(ids) || isCollectionEmpty(embeddings)) {
            log.info("[do not add empty embeddings to elasticsearch]");
            return;
        }
        ValidationUtils.ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ValidationUtils.ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        int size = ids.size();
        for (int i = 0; i < size; i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = embedded == null ? null : embedded.get(i);
            Map<byte[], byte[]> vectorField = new HashMap<>();
            vectorField.put(schema.getVectorFieldName().getBytes(), ToByteArray(embedding.vector()));
            if (textSegment != null) {
                vectorField.put(schema.getScalarFieldName().getBytes(), textSegment.text().getBytes());
                vectorField.put(schema.getMetadataFieldName().getBytes(), GSON.toJson(textSegment.metadata().asMap()).getBytes());
            }
            String key = schema.getPrefix() + id;
            client.hset(key.getBytes(), vectorField);
        }
    }

    @SuppressWarnings("unchecked")
    private List<EmbeddingMatch<TextSegment>> toEmbeddingMatch(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        return documents.stream().map(document -> {
            Double score = Double.parseDouble(document.getString(RedisSchema.SCORE_FIELD_NAME));
            String id = document.getId().substring(schema.getPrefix().length());
            String text = document.hasProperty(schema.getScalarFieldName()) ? document.getString(schema.getScalarFieldName()) : null;
            TextSegment embedded = null;
            if (text != null) {
                Metadata metadata = new Metadata(GSON.fromJson(document.getString(schema.getMetadataFieldName()), Map.class));
                embedded = new TextSegment(text, metadata);
            }
            // FIXME: This is not accurate embedding results, depending on the encoding of Redis.
            // But I think embedding is not important in many use case. Even LangChain do not return
            Embedding embedding = new Embedding(bytesToFloats(document.getString(schema.getVectorFieldName()).getBytes(ISO_8859_1)));
            return new EmbeddingMatch<>(score, id, embedding, embedded);
        }).collect(Collectors.toList());
    }

    private float[] bytesToFloats(byte[] bytes) {
        float[] output = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(output);
        return output;
    }
}
