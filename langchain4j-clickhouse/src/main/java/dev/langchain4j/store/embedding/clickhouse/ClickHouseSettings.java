package dev.langchain4j.store.embedding.clickhouse;

import java.util.*;

public class ClickHouseSettings {

    static final List<String> REQUIRED_COLUMN_MAP_KEYS = Arrays.asList("text", "id", "embedding");

    private String url;
    private String username;
    private String password;
    private String database;
    private String table;
    /**
     * Column type map to project column name onto langchain semantics.
     * <p>Must have keys: `text`, `id`, `embedding`</p>
     * <p>Optional key: metadata</p>
     */
    private Map<String, String> columnMap;
    private Integer dimension;
    private Properties properties;

    public ClickHouseSettings() {
        // init default value
        Map<String, String> defaultColumnMap = new HashMap<>();
        defaultColumnMap.put("id", "id");
        defaultColumnMap.put("text", "text");
        defaultColumnMap.put("embedding", "embedding");
        defaultColumnMap.put("metadata", "metadata");
        this.columnMap = defaultColumnMap;
        this.database = "default";
        this.url = "jdbc:clickhouse://localhost:8123/" + this.database;
        this.table = "langchain4j_clickhouse_example";
        this.dimension = 1536;
        this.properties = new Properties();
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

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }
}
