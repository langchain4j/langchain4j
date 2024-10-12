package dev.langchain4j.store.embedding.clickhouse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.command.CommandResponse;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.metrics.ServerMetrics;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.Records;
import com.clickhouse.data.ClickHouseFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.store.embedding.clickhouse.ClickHouseJsonUtils.toJson;
import static dev.langchain4j.store.embedding.clickhouse.ClickHouseJsonUtils.toObject;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * TODO: javadoc
 */
public class ClickHouseEmbeddingStore implements EmbeddingStore<TextSegment>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseEmbeddingStore.class);

    private final Client client;
    private final ClickHouseSettings settings;

    public ClickHouseEmbeddingStore(Client client,
                                    ClickHouseSettings settings) {
        this.settings = ensureNotNull(settings, "settings");

        // init client
        this.client = Optional.ofNullable(client).orElse(new Client.Builder()
                .addEndpoint(settings.getUrl())
                .setUsername(settings.getUsername())
                .setPassword(settings.getPassword())
                .build());

        // init experiment features and create table
        createTable();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    public static class Builder {

        private Client client;
        private ClickHouseSettings settings;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder settings(ClickHouseSettings settings) {
            this.settings = settings;
            return this;
        }

        public ClickHouseEmbeddingStore build() {
            return new ClickHouseEmbeddingStore(client, settings);
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
        try (Records records = client.queryRecords(buildQuerySql(referenceEmbedding, maxResults)).get(settings.getTimeout(), TimeUnit.MILLISECONDS)) {

            List<EmbeddingMatch<TextSegment>> relevantList = new ArrayList<>();
            for (GenericRecord result : records) {
                String id = result.getString(settings.getColumnMap().get("id"));
                String text = result.getString(settings.getColumnMap().get("text"));
                double[] doubleEmbedding = result.getDoubleArray(settings.getColumnMap().get("embedding"));
                float[] embedding = new float[doubleEmbedding.length];

                for (int i = 0; i < doubleEmbedding.length; i++) {
                    embedding[i] = (float) doubleEmbedding[i];
                }

                TextSegment textSegment = null;
                if (text != null) {
                    Metadata metadata = new Metadata();
                    if (settings.containsMetadata()) {
                        String metadataStr = result.getString("metadata");
                        Map<String, Object> metadataMap = (metadataStr == null || metadataStr.isEmpty()) ? new HashMap<>() :
                                toObject(metadataStr, new TypeReference<Map<String, Object>>() {
                                });
                        metadata = Metadata.from(Optional.ofNullable(metadataMap).orElse(new HashMap<>()));
                    }
                    textSegment = TextSegment.from(text, metadata);
                }
                double cosineDistance = result.getDouble("dist");
                EmbeddingMatch<TextSegment> embeddingMatch = new EmbeddingMatch<>(RelevanceScore.fromCosineSimilarity(1 - cosineDistance), id, Embedding.from(embedding), textSegment);
                relevantList.add(embeddingMatch);
            }

            return relevantList.stream()
                    .filter(relevant -> relevant.score() >= minScore)
                    .collect(toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        // TODO
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        // TODO
    }

    @Override
    public void removeAll() {
        client.execute(String.format("TRUNCATE TABLE IF EXISTS %s.%s", settings.getDatabase(), settings.getTable()));
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("ClickhouseEmbeddingStore don't add empty embeddings to ClickHouse");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");
        int length = ids.size();

        List<Map<String, Object>> dataList = new ArrayList<>();
        Map<String, String> columnMap = settings.getColumnMap();
        for (int i = 0; i < length; i++) {
            Map<String, Object> data = new HashMap<>(4);
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            TextSegment segment = embedded == null ? null : embedded.get(i);

            Float[] insertEmbedding = embedding.vectorAsList().toArray(new Float[0]);
            Metadata metadata = segment == null ? null : segment.metadata();

            data.put(columnMap.get("id"), id);
            data.put(columnMap.get("embedding"), insertEmbedding);
            data.put(columnMap.get("text"), segment == null ? null : segment.text());
            data.put(columnMap.get("metadata"), metadata == null || !settings.containsMetadata() ?
                    null : metadata.toMap());

            dataList.add(data);
        }

        String json = toJson(dataList);
        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        try (InsertResponse response = client.insert(settings.getTable(), inputStream, ClickHouseFormat.JSONEachRow).get(settings.getTimeout(), TimeUnit.MILLISECONDS)) {
            if (log.isDebugEnabled()) {
                log.debug("Insert finished: {} rows written", response.getMetrics().getMetric(ServerMetrics.NUM_ROWS_WRITTEN).getLong());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createTable() {
        String metadataColumn = settings.containsMetadata() ? String.format("%s JSON,", settings.getColumnMap().get("metadata")) : "";
        String createTableSql = String.format("CREATE TABLE IF NOT EXISTS %s.%s(" +
                        "%s String," +
                        "%s Nullable(String)," +
                        "%s Array(Float64)," +
                        "%s" +
                        "CONSTRAINT cons_vec_len CHECK length(%s) = %d," +
                        "INDEX vec_idx %s TYPE vector_similarity('hnsw', 'cosineDistance') GRANULARITY 1000" +
                        ") ENGINE = MergeTree ORDER BY id SETTINGS index_granularity = 8192 " +
                        "SETTINGS allow_experimental_json_type = 1, " +
                        "allow_experimental_object_type = 1, " +
                        "allow_experimental_vector_similarity_index = 1",
                settings.getDatabase(), settings.getTable(), settings.getColumnMap().get("id"),
                settings.getColumnMap().get("text"), settings.getColumnMap().get("embedding"),
                metadataColumn, settings.getColumnMap().get("embedding"),
                settings.getDimension(), settings.getColumnMap().get("embedding"));

        CompletableFuture<CommandResponse> response = client.execute(createTableSql);
        response.join();
    }

    private String buildQuerySql(Embedding refEmbedding, int maxResults) {
        String refEmbeddingStr = "[" + refEmbedding.vectorAsList().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
        List<String> queryColumnList = new ArrayList<>(Arrays.asList(settings.getColumnMap().get("id"), settings.getColumnMap().get("text"), settings.getColumnMap().get("embedding")));
        if (settings.containsMetadata()) {
            queryColumnList.add(settings.getColumnMap().get("metadata"));
        }
        return String.format(
                "SELECT %s, dist " +
                        "FROM %s.%s " +
                        "ORDER BY cosineDistance(%s, %s) AS dist ASC " +
                        "LIMIT %d",
                String.join(",", queryColumnList), settings.getDatabase(), settings.getTable(),
                settings.getColumnMap().get("embedding"), refEmbeddingStr, maxResults);
    }
}
