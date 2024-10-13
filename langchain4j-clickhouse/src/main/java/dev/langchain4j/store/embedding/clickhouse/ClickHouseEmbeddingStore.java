package dev.langchain4j.store.embedding.clickhouse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.metrics.ServerMetrics;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.Records;
import com.clickhouse.data.ClickHouseDataType;
import com.clickhouse.data.ClickHouseFormat;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.*;
import dev.langchain4j.store.embedding.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.store.embedding.clickhouse.ClickHouseJsonUtils.toJson;
import static dev.langchain4j.store.embedding.clickhouse.ClickHouseMappingKey.*;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * <p>
 * An <code>EmbeddingStore</code> which uses AI Vector Search capabilities of ClickHouse Database. This embedding store
 * supports metadata filtering and removal
 * </p><p>
 * Instances of this store are created by configuring a builder:
 * </p><pre>{@code
 * EmbeddingStore<TextSegment> example() {
 *   return ClickHouseEmbeddingStore.builder()
 *     .settings(ClickHouseSettings.builder()
 *             .url("http://localhost:8123")
 *             .dimension(embeddingModel.dimension())
 *             .build();)
 *     .build();
 * }
 * }</pre><p>
 * It is recommended to configure a {@link com.clickhouse.client.ClickHouseClient} in order to customize your connection or authorization.
 * </p><p>
 * This embedding store requires a {@link ClickHouseSettings} to be configured
 * </p>
 */
public class ClickHouseEmbeddingStore implements EmbeddingStore<TextSegment>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseEmbeddingStore.class);

    /**
     * Client to connect ClickHouse server.
     */
    private final Client client;
    /**
     * ClickHouse settings.
     *
     * @see ClickHouseSettings
     */
    private final ClickHouseSettings settings;
    /**
     * ClickHouse filter mapper.
     */
    private final ClickHouseMetadataFilterMapper filterMapper;

    /**
     * Construct embedding store.
     *
     * @param client   Custom ClickHouse client. (Optional)
     * @param settings ClickHouse settings.
     * @see ClickHouseSettings
     */
    public ClickHouseEmbeddingStore(Client client, ClickHouseSettings settings) {
        this.settings = ensureNotNull(settings, "settings");
        this.filterMapper = new ClickHouseMetadataFilterMapper(settings.getColumnMap(), settings.getMetadataTypeMap());

        // init client
        this.client = Optional.ofNullable(client)
                .orElse(new Client.Builder()
                        .addEndpoint(settings.getUrl())
                        .setUsername(settings.getUsername())
                        .setPassword(settings.getPassword())
                        .serverSetting("allow_experimental_vector_similarity_index", "1")
                        .build()
                );

        createDatabase();
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

        /**
         * Configure custom ClickHouse client
         *
         * @param client Custom ClickHouse client
         */
        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        /**
         * Configure ClickHouse settings
         *
         * @param settings ClickHouse settings
         * @see ClickHouseSettings
         */
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
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        try (Records records = client.queryRecords(buildQuerySql(request)).get(settings.getTimeout(), TimeUnit.MILLISECONDS)) {
            List<EmbeddingMatch<TextSegment>> relevantList = new ArrayList<>();
            records.forEach(r -> relevantList.add(toEmbeddingMatch(r)));

            return new EmbeddingSearchResult<>(relevantList.stream().filter(relevant -> relevant.score() >= request.minScore()).collect(toList()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        removeAll(metadataKey(settings.getColumnMapping(ID_MAPPING_KEY)).isIn(ids));
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");

        String whereClause = "WHERE " + filterMapper.map(filter);
        client.execute(String.format("DELETE FROM %s.%s %s", settings.getDatabase(), settings.getTable(), whereClause));
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
        for (int i = 0; i < length; i++) {
            dataList.add(toInsertData(ids.get(i), embeddings.get(i), embedded == null ? null : embedded.get(i)));
        }

        String json = toJson(dataList);
        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        try (InsertResponse response = client.insert(settings.getTable(), inputStream, ClickHouseFormat.JSON).get(settings.getTimeout(), TimeUnit.MILLISECONDS)) {
            if (log.isDebugEnabled()) {
                log.debug("Insert finished: {} rows written", response.getMetrics().getMetric(ServerMetrics.NUM_ROWS_WRITTEN).getLong());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createDatabase() {
        client.execute(String.format("CREATE DATABASE IF NOT EXISTS %s", settings.getDatabase()));
    }

    private void createTable() {
        final String TEMPLATE = "%s Nullable(%s)";
        List<String> metadataColumns = new ArrayList<>();
        if (settings.containsMetadata()) {
            for (Map.Entry<String, ClickHouseDataType> entry : settings.getMetadataTypeMap().entrySet()) {
                metadataColumns.add(String.format(TEMPLATE, entry.getKey(), entry.getValue().name()));
            }
        }
        String metadataCreateSql = metadataColumns.isEmpty() ? "" : String.join(",", metadataColumns) + ", ";

        String createTableSql = String.format("CREATE TABLE IF NOT EXISTS %s.%s(" +
                        "%s String," +
                        "%s Nullable(String)," +
                        "%s Array(Float64)," +
                        "%s" +
                        "CONSTRAINT cons_vec_len CHECK length(%s) = %d," +
                        "INDEX vec_idx %s TYPE vector_similarity('hnsw', 'cosineDistance') GRANULARITY 1000" +
                        ") ENGINE = MergeTree ORDER BY id SETTINGS index_granularity = 8192",
                settings.getDatabase(), settings.getTable(), settings.getColumnMapping(ID_MAPPING_KEY),
                settings.getColumnMapping(TEXT_MAPPING_KEY), settings.getColumnMapping(EMBEDDING_MAPPING_KEY),
                metadataCreateSql, settings.getColumnMapping(EMBEDDING_MAPPING_KEY), settings.getDimension(),
                settings.getColumnMapping(EMBEDDING_MAPPING_KEY));

        client.execute(createTableSql);
    }

    private String buildQuerySql(EmbeddingSearchRequest request) {
        Embedding refEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        Filter filter = request.filter();
        String whereClause = filter == null ? "" : String.format("WHERE %s", filterMapper.map(filter));

        String refEmbeddingStr = "[" + refEmbedding.vectorAsList().stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
        List<String> queryColumnList = new ArrayList<>(Arrays.asList(settings.getColumnMapping(ID_MAPPING_KEY), settings.getColumnMapping(TEXT_MAPPING_KEY), settings.getColumnMapping(EMBEDDING_MAPPING_KEY)));
        if (settings.containsMetadata()) {
            queryColumnList.addAll(settings.getMetadataTypeMap().keySet());
        }

        return String.format("WITH %s AS reference_vector " +
                        "SELECT %s, dist " +
                        "FROM %s.%s " +
                        "%s " +
                        "ORDER BY cosineDistance(%s, reference_vector) AS %s ASC " +
                        "LIMIT %d",
                refEmbeddingStr, String.join(",", queryColumnList), settings.getDatabase(), settings.getTable(),
                whereClause, settings.getColumnMapping(EMBEDDING_MAPPING_KEY), DISTANCE_COLUMN_NAME, maxResults);
    }

    private EmbeddingMatch<TextSegment> toEmbeddingMatch(GenericRecord r) {
        String id = r.getString(settings.getColumnMapping(ID_MAPPING_KEY));
        String text = r.getString(settings.getColumnMapping(TEXT_MAPPING_KEY));
        double[] doubleEmbedding = r.getDoubleArray(settings.getColumnMapping(EMBEDDING_MAPPING_KEY));
        float[] embedding = new float[doubleEmbedding.length];

        for (int i = 0; i < doubleEmbedding.length; i++) {
            embedding[i] = (float) doubleEmbedding[i];
        }

        TextSegment textSegment = null;
        if (text != null) {
            Metadata metadata = new Metadata();
            if (settings.containsMetadata()) {
                Map<String, Object> searchedMetadata = new HashMap<>();
                for (String metadataKey : settings.getMetadataTypeMap().keySet()) {
                    Object val = r.getObject(metadataKey);
                    if (val != null) {
                        searchedMetadata.put(metadataKey, val);
                    }
                }
                metadata = Metadata.from(searchedMetadata);
            }
            textSegment = TextSegment.from(text, metadata);
        }
        double cosineDistance = r.getDouble(DISTANCE_COLUMN_NAME);
        return new EmbeddingMatch<>(RelevanceScore.fromCosineSimilarity(1 - cosineDistance), id, Embedding.from(embedding), textSegment);
    }

    private Map<String, Object> toInsertData(String id, Embedding embedding, TextSegment segment) {
        Map<String, Object> data = new HashMap<>(4);
        Float[] insertEmbedding = embedding.vectorAsList().toArray(new Float[0]);
        Map<String, Object> metadata = segment == null ? null : segment.metadata().toMap();

        data.put(settings.getColumnMapping(ID_MAPPING_KEY), id);
        data.put(settings.getColumnMapping(EMBEDDING_MAPPING_KEY), insertEmbedding);
        data.put(settings.getColumnMapping(TEXT_MAPPING_KEY), segment == null ? null : segment.text());

        // We need to set all column, including null.
        if (settings.containsMetadata()) {
            Map<String, ClickHouseDataType> meatadataColumnMap = settings.getMetadataTypeMap();
            for (String key : meatadataColumnMap.keySet()) {
                data.put(key, Optional.ofNullable(metadata).map(m -> m.get(key)).orElse(null));
            }
        }

        return data;
    }
}
