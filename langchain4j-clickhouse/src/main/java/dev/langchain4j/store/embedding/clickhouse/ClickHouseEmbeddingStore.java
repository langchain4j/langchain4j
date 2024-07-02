package dev.langchain4j.store.embedding.clickhouse;

import com.clickhouse.jdbc.ClickHouseDataSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static dev.langchain4j.store.embedding.clickhouse.ClickHouseSettings.REQUIRED_COLUMN_MAP_KEYS;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class ClickHouseEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseEmbeddingStore.class);
    private final ClickHouseDataSource dataSource;
    private final ClickHouseSettings settings;
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    public ClickHouseEmbeddingStore(ClickHouseDataSource dataSource,
                                    ClickHouseSettings settings) {
        this.settings = ensureNotNull(settings, "setting");
        checkColumnMap(settings.getColumnMap());
        // init dataSource
        try {
            this.dataSource = Optional.ofNullable(dataSource).orElse(new ClickHouseDataSource(settings.getUrl(), new Properties()));
        } catch (SQLException e) {
            String errMsg = "encounter exception when initialize dataSource";
            log.error(errMsg, e);
            throw new ClickhouseOperationException(errMsg);
        }

        // init experimental feature and table
        try (Connection conn = createConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(buildCreateTableSql());
        } catch (SQLException e) {
            String errMsg = String.format("encounter exception when creating table %s", e.getLocalizedMessage());
            log.error(errMsg, e);
            throw new ClickhouseOperationException(errMsg);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ClickHouseDataSource dataSource;
        private ClickHouseSettings settings = new ClickHouseSettings();

        public Builder dataSource(ClickHouseDataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder url(String url) {
            settings.setUrl(url);
            return this;
        }

        public Builder username(String username) {
            settings.setUsername(username);
            return this;
        }

        public Builder password(String password) {
            settings.setPassword(password);
            return this;
        }

        public Builder database(String database) {
            settings.setDatabase(database);
            return this;
        }

        public Builder table(String table) {
            settings.setTable(table);
            return this;
        }

        public Builder dimension(int dimension) {
            settings.setDimension(dimension);
            return this;
        }

        /**
         * Column type map to project column name onto langchain semantics.
         * <p>Must have keys: `text`, `id`, `embedding`</p>
         * <p>Optional key: metadata</p>
         *
         * @param columnMap column map
         * @return builder
         */
        public Builder columnMap(Map<String, String> columnMap) {
            settings.setColumnMap(columnMap);
            return this;
        }

        public Builder properties(Properties properties) {
            settings.setProperties(properties);
            return this;
        }

        public Builder setting(ClickHouseSettings setting) {
            this.settings = setting;
            return this;
        }

        public ClickHouseEmbeddingStore build() {
            return new ClickHouseEmbeddingStore(dataSource, settings);
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
        try (Connection conn = createConnection();
             Statement stmt = conn.createStatement();
             ResultSet resultSet = stmt.executeQuery(buildQuerySql(referenceEmbedding, maxResults))) {

            List<EmbeddingMatch<TextSegment>> relevantList = new ArrayList<>();
            while (resultSet.next()) {
                String id = resultSet.getString(settings.getColumnMap().get("id"));
                String text = resultSet.getString(settings.getColumnMap().get("text"));
                float[] embedding = (float[]) resultSet.getArray(settings.getColumnMap().get("embedding")).getArray();
                TextSegment textSegment = null;
                if (text != null) {
                    Metadata metadata = new Metadata();
                    if (settings.getColumnMap().containsKey("metadata")) {
                        String metadataStr = resultSet.getString("metadata");
                        TypeToken<Map<String, String>> typeToken = new TypeToken<Map<String, String>>() {
                        };
                        Map<String, String> metadataMap = GSON.fromJson(metadataStr, typeToken);
                        metadata = Metadata.from(Optional.ofNullable(metadataMap).orElse(new HashMap<>()));
                    }
                    textSegment = TextSegment.from(text, metadata);
                }
                double cosineDistance = resultSet.getDouble("dist");
                EmbeddingMatch<TextSegment> embeddingMatch = new EmbeddingMatch<>(RelevanceScore.fromCosineSimilarity(1 - cosineDistance), id, Embedding.from(embedding), textSegment);
                relevantList.add(embeddingMatch);
            }

            return relevantList.stream()
                    .filter(relevant -> relevant.score() >= minScore)
                    .collect(toList());
        } catch (SQLException e) {
            String errMsg = String.format("encounter exception when query data %s", e.getLocalizedMessage());
            log.error(errMsg, e);
            throw new ClickhouseOperationException(errMsg);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("do not add empty embeddings to ClickHouse");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");
        int length = ids.size();

        try (Connection conn = createConnection();
             PreparedStatement preparedStmt = conn.prepareStatement(buildInsertSql())) {
            for (int i = 0; i < length; i++) {
                String id = ids.get(i);
                Embedding embedding = embeddings.get(i);
                TextSegment text = embedded == null ? null : embedded.get(i);

                Float[] insertEmbedding = embedding.vectorAsList().toArray(new Float[0]);

                preparedStmt.setString(1, id);
                preparedStmt.setString(2, text == null ? null : text.text());
                preparedStmt.setArray(3, conn.createArrayOf("Float32", insertEmbedding));
                if (this.settings.getColumnMap().containsKey("metadata")) {
                    preparedStmt.setString(4, text == null ? null : GSON.toJson(text.metadata().asMap()));
                }
                preparedStmt.addBatch();
            }
            preparedStmt.executeBatch();
        } catch (SQLException e) {
            String errMsg = String.format("encounter exception when inserting data %s", e.getLocalizedMessage());
            log.error(errMsg, e);
            throw new ClickhouseOperationException(errMsg);
        }
    }

    private void checkColumnMap(Map<String, String> columnMap) {
        // check column map must contain all REQUIRED_COLUMN_MAP_KEYS
        for (String requiredKey : REQUIRED_COLUMN_MAP_KEYS) {
            if (!columnMap.containsKey(requiredKey)) {
                throw illegalArgument("columnMap must contains key %s", requiredKey);
            }
        }
    }

    private String buildCreateTableSql() {
        // init metadata column if exist
        String metadataColumn = "";
        if (settings.getColumnMap().containsKey("metadata")) {
            metadataColumn = String.format("%s Nullable(String),", settings.getColumnMap().get("metadata"));
        }
        return String.format("CREATE TABLE IF NOT EXISTS %s.%s(" +
                        "%s String," +
                        "%s Nullable(String)," +
                        "%s Array(Float32)," +
                        "%s" +
                        "CONSTRAINT cons_vec_len CHECK length(%s) = %d," +
                        "INDEX vec_idx %s TYPE annoy('cosineDistance', 100) GRANULARITY 1000" +
                        ") ENGINE = MergeTree ORDER BY id SETTINGS index_granularity = 8192 " +
                        "SETTINGS allow_experimental_annoy_index = 1",
                settings.getDatabase(), settings.getTable(), settings.getColumnMap().get("id"),
                settings.getColumnMap().get("text"), settings.getColumnMap().get("embedding"),
                metadataColumn, settings.getColumnMap().get("embedding"),
                settings.getDimension(), settings.getColumnMap().get("embedding"));
    }

    private String buildInsertSql() {
        List<String> insertColumnList = new ArrayList<>(Arrays.asList(settings.getColumnMap().get("id"), settings.getColumnMap().get("text"), settings.getColumnMap().get("embedding")));
        if (settings.getColumnMap().containsKey("metadata")) {
            insertColumnList.add(settings.getColumnMap().get("metadata"));
        }
        return String.format("INSERT INTO %s.%s(%s) VALUES (?, ?, ?, ?)",
                settings.getDatabase(), settings.getTable(), String.join(",", insertColumnList));
    }

    private String buildQuerySql(Embedding refEmbedding, int maxResults) {
        String refEmbeddingStr = "[" + refEmbedding.vectorAsList().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
        List<String> queryColumnList = new ArrayList<>(Arrays.asList(settings.getColumnMap().get("id"), settings.getColumnMap().get("text"), settings.getColumnMap().get("embedding")));
        if (settings.getColumnMap().containsKey("metadata")) {
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

    private Connection createConnection() throws SQLException {
        String username = this.settings.getUsername();
        return username == null ? this.dataSource.getConnection() :
                this.dataSource.getConnection(username, this.settings.getPassword());
    }
}
