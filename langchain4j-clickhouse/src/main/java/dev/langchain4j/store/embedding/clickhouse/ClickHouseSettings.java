package dev.langchain4j.store.embedding.clickhouse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * TODO: javadoc
 */
public class ClickHouseSettings {

    private static final Map<String, String> DEFAULT_COLUMN_MAP = new HashMap<>();
    private static final List<String> REQUIRED_COLUMN_MAP_KEYS = Arrays.asList("text", "id", "embedding");

    static {
        DEFAULT_COLUMN_MAP.put("text", "text");
        DEFAULT_COLUMN_MAP.put("id", "id");
        DEFAULT_COLUMN_MAP.put("embedding", "embedding");
    }

    private String url;
    private String username;
    private String password;
    private String database;
    private String table;
    /**
     * Column type map to project column name onto langchain4j semantics.
     * <p>Must have keys: `text`, `id`, `embedding`</p>
     * <p>Optional key: metadata</p>
     */
    private Map<String, String> columnMap;
    private Integer dimension;
    private Long timeout;

    public ClickHouseSettings(String url,
                              String username,
                              String password,
                              String database,
                              String table,
                              Map<String, String> columnMap,
                              Integer dimension,
                              Long timeout) {
        this.url = ensureNotNull(url, "url");
        this.username = username;
        this.password = password;
        this.database = getOrDefault(database, "default");
        this.table = getOrDefault(table, "langchain4j_table");
        this.columnMap = getOrDefault(columnMap, DEFAULT_COLUMN_MAP);
        this.dimension = ensureNotNull(dimension, "dimension");
        this.timeout = getOrDefault(timeout, 3000L);

        ensureColumnMap(this.columnMap);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public Map<String, String> getColumnMap() {
        return columnMap;
    }

    public void setColumnMap(Map<String, String> columnMap) {
        this.columnMap = columnMap;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public boolean containsMetadata() {
        return columnMap.containsKey("metadata");
    }

    private static Map<String, String> ensureColumnMap(Map<String, String> columnMap) {
        REQUIRED_COLUMN_MAP_KEYS.forEach(requiredColumn -> {
            if (!columnMap.containsKey(requiredColumn)) {
                throw illegalArgument("ColumnMap must contains key %s", requiredColumn);
            }
        });

        return columnMap;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String url;
        private String username;
        private String password;
        private String database;
        private String table;
        private Map<String, String> columnMap;
        private Integer dimension;
        private Long timeout;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder table(String table) {
            this.table = table;
            return this;
        }

        /**
         * Column type map to project column name onto langchain4j semantics.
         * <p>Must have keys: `text`, `id`, `embedding`</p>
         * <p>Optional key: metadata</p>
         *
         * @param columnMap column map
         */
        public Builder columnMap(Map<String, String> columnMap) {
            this.columnMap = columnMap;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder timeout(Long timeout) {
            this.timeout = timeout;
            return this;
        }

        public ClickHouseSettings build() {
            return new ClickHouseSettings(url, username, password, database, table, columnMap, dimension, timeout);
        }
    }
}
