package dev.langchain4j.store.embedding.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.MariaDBContainer;

class MariaDbEmbeddingCustomNamingIT {

    static MariaDBContainer<?> mariadbContainer = MariaDbTestUtils.defaultContainer;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        mariadbContainer.start();
    }

    @ParameterizedTest
    @CsvSource({
        "my_table,my_id,my_embedding,my_content,my_meta",
        "my_table2,my_id 2,my_embedding 2,my_content 2,`my_meta 2`",
        "`my_ta``ble3`,`my_id3`,`my_embedding3`,`my_content3`,`my_meta3`"
    })
    void jsonNaming(String tableName, String idName, String embeddingName, String contentName, String metadataJsonName)
            throws SQLException {
        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COMBINED_JSON)
                .columnDefinitions(Collections.singletonList(metadataJsonName + " JSON"))
                .build();
        EmbeddingStore<TextSegment> embeddingStore = MariaDbEmbeddingStore.builder()
                .url(mariadbContainer.getJdbcUrl())
                .user(mariadbContainer.getUsername())
                .password(mariadbContainer.getPassword())
                .table(tableName)
                .idFieldName(idName)
                .embeddingFieldName(embeddingName)
                .contentFieldName(contentName)
                .metadataStorageConfig(metadataStorageConfig)
                .dimension(384)
                .createTable(true)
                .dropTableFirst(true)
                .build();

        Embedding embedding = embeddingModel.embed("hello").content();
        String id = embeddingStore.add(embedding);
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<?> embeddingSearchResult = embeddingStore.search(embeddingSearchRequest);
        var relevant = embeddingSearchResult.matches();
        assertThat(relevant).hasSize(1);

        embeddingStore.remove(id);

        assertThat(isTableExists(tableName)).isTrue();
        assertThat(areColumnsExisting(tableName, new String[] {idName, embeddingName, contentName, metadataJsonName}))
                .isTrue();
        assertThat(isVectorIndexExists(tableName)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "my_table,my_id,my_embedding,my_content,my_meta",
        "my_table2,my_id 2,my_embedding 2,my_content 2",
        "`my_ta``ble3`,`my_id3`,`my_embedding3`,`my_content3`"
    })
    void colsNaming(String tableName, String idName, String embeddingName, String contentName) throws SQLException {
        MetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Arrays.asList("key varchar(255)", "`name` varchar(255)", "`age` float"))
                .indexes(Arrays.asList("key", "name"))
                .build();
        EmbeddingStore<TextSegment> embeddingStore = MariaDbEmbeddingStore.builder()
                .url(mariadbContainer.getJdbcUrl())
                .user(mariadbContainer.getUsername())
                .password(mariadbContainer.getPassword())
                .table(tableName)
                .idFieldName(idName)
                .embeddingFieldName(embeddingName)
                .contentFieldName(contentName)
                .metadataStorageConfig(metadataStorageConfig)
                .dimension(384)
                .createTable(true)
                .dropTableFirst(true)
                .build();

        Embedding embedding = embeddingModel.embed("hello").content();
        String id = embeddingStore.add(embedding);
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<?> embeddingSearchResult = embeddingStore.search(embeddingSearchRequest);
        var relevant = embeddingSearchResult.matches();
        assertThat(relevant).hasSize(1);

        embeddingStore.remove(id);

        assertThat(isTableExists(tableName)).isTrue();
        assertThat(areColumnsExisting(
                        tableName, new String[] {idName, embeddingName, contentName, "key", "name", "age"}))
                .isTrue();
        assertThat(isColIndexExists(tableName, new String[] {"key", "name"})).isTrue();
    }

    private static boolean isVectorIndexExists(String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                mariadbContainer.getJdbcUrl(), mariadbContainer.getUsername(), mariadbContainer.getPassword())) {
            String sql = "SELECT EXISTS (SELECT * FROM information_schema.statistics WHERE"
                    + " TABLE_SCHEMA=? AND TABLE_NAME=? AND INDEX_TYPE='VECTOR')";
            try (PreparedStatement prep = connection.prepareStatement(sql)) {
                prep.setString(1, mariadbContainer.getDatabaseName());
                prep.setString(2, removeQuotes(tableName));
                ResultSet rs = prep.executeQuery();
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean isColIndexExists(String tableName, String[] indexFieldNames) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                mariadbContainer.getJdbcUrl(), mariadbContainer.getUsername(), mariadbContainer.getPassword())) {

            String sql = "SELECT EXISTS (SELECT * FROM information_schema.statistics WHERE table_schema="
                    + " ? AND  table_name = ? AND column_name = ?)";
            try (PreparedStatement prep = connection.prepareStatement(sql)) {
                for (String field : indexFieldNames) {
                    prep.setString(1, mariadbContainer.getDatabaseName());
                    prep.setString(2, removeQuotes(tableName));
                    prep.setString(3, removeQuotes(field));
                    ResultSet rs = prep.executeQuery();
                    if (!rs.next() || !rs.getBoolean(1)) return false;
                }
            }
        }
        return true;
    }

    @SuppressWarnings("null")
    private static boolean isTableExists(String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                mariadbContainer.getJdbcUrl(), mariadbContainer.getUsername(), mariadbContainer.getPassword())) {
            String sql = "SELECT EXISTS (SELECT * FROM information_schema.tables WHERE table_schema= ?"
                    + " AND table_name = ?)";
            try (PreparedStatement prep = connection.prepareStatement(sql)) {
                prep.setString(1, mariadbContainer.getDatabaseName());
                prep.setString(2, removeQuotes(tableName));
                ResultSet rs = prep.executeQuery();
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean areColumnsExisting(String tableName, String[] fieldNames) throws SQLException {

        try (Connection connection = DriverManager.getConnection(
                mariadbContainer.getJdbcUrl(), mariadbContainer.getUsername(), mariadbContainer.getPassword())) {

            String sql = "SELECT EXISTS (SELECT * FROM information_schema.columns WHERE table_schema= ?"
                    + " AND  table_name = ? AND column_name = ?)";
            try (PreparedStatement prep = connection.prepareStatement(sql)) {
                for (String field : fieldNames) {
                    prep.setString(1, mariadbContainer.getDatabaseName());
                    prep.setString(2, removeQuotes(tableName));
                    prep.setString(3, removeQuotes(field));
                    ResultSet rs = prep.executeQuery();
                    if (!rs.next() || !rs.getBoolean(1)) return false;
                }
            }
        }
        return true;
    }

    private static String removeQuotes(String field) {
        return field.charAt(0) == '`' ? field.substring(1, field.length() - 1) : field;
    }
}
