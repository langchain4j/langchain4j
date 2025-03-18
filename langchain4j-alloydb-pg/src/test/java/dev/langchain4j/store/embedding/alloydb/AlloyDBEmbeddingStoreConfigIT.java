package dev.langchain4j.store.embedding.alloydb;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.utils.AlloyDBTestUtils.randomVector;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.engine.AlloyDBEngine;
import dev.langchain4j.engine.EmbeddingStoreConfig;
import dev.langchain4j.engine.MetadataColumn;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.index.DistanceStrategy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AlloyDBEmbeddingStoreConfigIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);
    private static final String TABLE_NAME = "JAVA_EMBEDDING_TEST_TABLE";
    private static final Integer VECTOR_SIZE = 384;
    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static EmbeddingStoreConfig embeddingStoreConfig;
    private static String projectId;
    private static String region;
    private static String cluster;
    private static String instance;
    private static String database;
    private static String user;
    private static String password;

    private static AlloyDBEngine engine;
    private static AlloyDBEmbeddingStore store;
    private static Connection defaultConnection;

    @BeforeAll
    public static void beforeAll() throws SQLException {
        engine = new AlloyDBEngine.Builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .build();

        List<MetadataColumn> metadataColumns = new ArrayList<>();
        metadataColumns.add(new MetadataColumn("string", "text", true));
        metadataColumns.add(new MetadataColumn("uuid", "uuid", true));
        metadataColumns.add(new MetadataColumn("integer", "integer", true));
        metadataColumns.add(new MetadataColumn("long", "bigint", true));
        metadataColumns.add(new MetadataColumn("float", "real", true));
        metadataColumns.add(new MetadataColumn("double", "double precision", true));

        embeddingStoreConfig = new EmbeddingStoreConfig.Builder(TABLE_NAME, VECTOR_SIZE)
                .metadataColumns(metadataColumns)
                .storeMetadata(true)
                .build();

        defaultConnection = engine.getConnection();

        defaultConnection.createStatement().executeUpdate(String.format("DROP TABLE IF EXISTS \"%s\"", TABLE_NAME));

        engine.initVectorStoreTable(embeddingStoreConfig);

        List<String> metaColumnNames =
                metadataColumns.stream().map(c -> c.getName()).collect(Collectors.toList());

        store = new AlloyDBEmbeddingStore.Builder(engine, TABLE_NAME)
                .distanceStrategy(DistanceStrategy.COSINE_DISTANCE)
                .metadataColumns(metaColumnNames)
                .build();
    }

    @AfterEach
    public void afterEach() throws SQLException {
        defaultConnection.createStatement().executeUpdate(String.format("TRUNCATE TABLE \"%s\"", TABLE_NAME));
    }

    @AfterAll
    public static void afterAll() throws SQLException {
        defaultConnection.createStatement().executeUpdate(String.format("DROP TABLE IF EXISTS \"%s\"", TABLE_NAME));
        defaultConnection.close();
    }

    @Test
    void add_single_embedding_to_store() throws SQLException {
        float[] vector = randomVector(VECTOR_SIZE);
        Embedding embedding = new Embedding(vector);
        String id = store.add(embedding);

        try (Statement statement = defaultConnection.createStatement(); ) {
            ResultSet rs = statement.executeQuery(String.format(
                    "SELECT \"%s\" FROM \"%s\" WHERE \"%s\" = '%s'",
                    embeddingStoreConfig.getEmbeddingColumn(), TABLE_NAME, embeddingStoreConfig.getIdColumn(), id));
            rs.next();
            String response = rs.getString(embeddingStoreConfig.getEmbeddingColumn());
            assertThat(response)
                    .isEqualTo(Arrays.toString(vector).replaceAll(" ", "").replaceAll(".0,", ","));
        }
    }

    @Test
    void add_embeddings_list_to_store() throws SQLException {
        List<String> expectedVectors = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            float[] vector = randomVector(VECTOR_SIZE);
            expectedVectors.add(Arrays.toString(vector).replaceAll(" ", "").replaceAll(".0,", ","));
            embeddings.add(new Embedding(vector));
        }
        List<String> ids = store.addAll(embeddings);
        String stringIds = ids.stream().map(id -> String.format("'%s'", id)).collect(Collectors.joining(","));

        try (Statement statement = defaultConnection.createStatement(); ) {
            ResultSet rs = statement.executeQuery(String.format(
                    "SELECT \"%s\" FROM \"%s\" WHERE \"%s\" IN (%s)",
                    embeddingStoreConfig.getEmbeddingColumn(),
                    TABLE_NAME,
                    embeddingStoreConfig.getIdColumn(),
                    stringIds));
            while (rs.next()) {
                String response = rs.getString(embeddingStoreConfig.getEmbeddingColumn());
                assertThat(expectedVectors).contains(response);
            }
        }
    }

    @Test
    void add_single_embedding_with_id_to_store() throws SQLException {
        float[] vector = randomVector(VECTOR_SIZE);
        Embedding embedding = new Embedding(vector);
        String id = randomUUID();
        store.add(id, embedding);

        try (Statement statement = defaultConnection.createStatement(); ) {
            ResultSet rs = statement.executeQuery(String.format(
                    "SELECT \"%s\" FROM \"%s\" WHERE \"%s\" = '%s'",
                    embeddingStoreConfig.getEmbeddingColumn(), TABLE_NAME, embeddingStoreConfig.getIdColumn(), id));
            rs.next();
            String response = rs.getString(embeddingStoreConfig.getEmbeddingColumn());
            assertThat(response)
                    .isEqualTo(Arrays.toString(vector).replaceAll(" ", "").replaceAll(".0,", ","));
        }
    }

    @Test
    void add_single_embedding_with_content_to_store() throws SQLException, JsonProcessingException {
        float[] vector = randomVector(VECTOR_SIZE);
        Embedding embedding = new Embedding(vector);

        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("string", "s");
        metaMap.put("uuid", UUID.randomUUID());
        metaMap.put("integer", 1);
        metaMap.put("long", 1L);
        metaMap.put("float", 1f);
        metaMap.put("double", 1d);
        metaMap.put("extra", "not in table columns");
        metaMap.put("extra_credits", 10);
        Metadata metadata = new Metadata(metaMap);
        TextSegment textSegment = new TextSegment("this is a test text", metadata);
        String id = store.add(embedding, textSegment);

        String metadataColumnNames = metaMap.entrySet().stream()
                .filter(e -> !e.getKey().contains("extra"))
                .map(e -> "\"" + e.getKey() + "\"")
                .collect(Collectors.joining(", "));

        try (Statement statement = defaultConnection.createStatement(); ) {
            ResultSet rs = statement.executeQuery(String.format(
                    "SELECT \"%s\", %s, \"%s\" FROM \"%s\" WHERE \"%s\" = '%s'",
                    embeddingStoreConfig.getEmbeddingColumn(),
                    metadataColumnNames,
                    embeddingStoreConfig.getMetadataJsonColumn(),
                    TABLE_NAME,
                    embeddingStoreConfig.getIdColumn(),
                    id));
            Map<String, Object> extraMetaMap = new HashMap<>();
            Map<String, Object> metadataJsonMap = null;
            while (rs.next()) {
                String response = rs.getString(embeddingStoreConfig.getEmbeddingColumn());
                assertThat(response)
                        .isEqualTo(Arrays.toString(vector).replaceAll(" ", "").replaceAll(".0,", ","));
                for (String column : metaMap.keySet()) {
                    if (column.contains("extra")) {
                        extraMetaMap.put(column, metaMap.get(column));
                    } else {
                        assertThat(rs.getObject(column)).isEqualTo(metaMap.get(column));
                    }
                }
                String metadataJsonString =
                        getOrDefault(rs.getString(embeddingStoreConfig.getMetadataJsonColumn()), "{}");
                metadataJsonMap = OBJECT_MAPPER.readValue(metadataJsonString, Map.class);
            }
            assertThat(extraMetaMap.size()).isEqualTo(metadataJsonMap.size());
            for (String key : extraMetaMap.keySet()) {
                assertThat(extraMetaMap.get(key).equals((metadataJsonMap.get(key))))
                        .isTrue();
            }
        }
    }

    @Test
    void add_embeddings_list_and_content_list_to_store() throws SQLException, JsonProcessingException {
        Map<String, Integer> expectedVectorsAndIndexes = new HashMap<>();
        Map<Integer, Map<String, Object>> metaMaps = new HashMap<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> textSegments = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            float[] vector = randomVector(VECTOR_SIZE);
            expectedVectorsAndIndexes.put(
                    Arrays.toString(vector).replaceAll(" ", "").replaceAll(".0,", ","), i);
            embeddings.add(new Embedding(vector));
            Map<String, Object> metaMap = new HashMap<>();
            metaMap.put("string", "s" + i);
            metaMap.put("uuid", UUID.randomUUID());
            metaMap.put("integer", i);
            metaMap.put("long", 1L);
            metaMap.put("float", 1f);
            metaMap.put("double", 1d);
            metaMap.put("extra", "not in table columns " + i);
            metaMap.put("extra_credits", 100 + i);
            metaMaps.put(i, metaMap);
            Metadata metadata = new Metadata(metaMap);
            textSegments.add(new TextSegment("this is a test text " + i, metadata));
        }

        List<String> ids = store.addAll(embeddings, textSegments);
        String stringIds = ids.stream().map(id -> String.format("'%s'", id)).collect(Collectors.joining(","));

        String metadataColumnNames = metaMaps.get(0).entrySet().stream()
                .filter(e -> !e.getKey().contains("extra"))
                .map(e -> "\"" + e.getKey() + "\"")
                .collect(Collectors.joining(", "));

        try (Statement statement = defaultConnection.createStatement(); ) {
            ResultSet rs = statement.executeQuery(String.format(
                    "SELECT \"%s\", %s ,\"%s\" FROM \"%s\" WHERE \"%s\" IN (%s)",
                    embeddingStoreConfig.getEmbeddingColumn(),
                    metadataColumnNames,
                    embeddingStoreConfig.getMetadataJsonColumn(),
                    TABLE_NAME,
                    embeddingStoreConfig.getIdColumn(),
                    stringIds));
            Map<String, Object> extraMetaMap = new HashMap<>();
            Map<String, Object> metadataJsonMap = null;
            while (rs.next()) {
                String response = rs.getString(embeddingStoreConfig.getEmbeddingColumn());
                assertThat(expectedVectorsAndIndexes.keySet()).contains(response);
                int index = expectedVectorsAndIndexes.get(response);
                for (String column : metaMaps.get(index).keySet()) {
                    if (column.contains("extra")) {
                        extraMetaMap.put(column, metaMaps.get(index).get(column));
                    } else {
                        assertThat(rs.getObject(column))
                                .isEqualTo(metaMaps.get(index).get(column));
                    }
                }
                String metadataJsonString =
                        getOrDefault(rs.getString(embeddingStoreConfig.getMetadataJsonColumn()), "{}");
                metadataJsonMap = OBJECT_MAPPER.readValue(metadataJsonString, Map.class);
            }
            assertThat(metadataJsonMap).isNotNull();
            assertThat(extraMetaMap.size()).isEqualTo(metadataJsonMap.size());
            for (String key : extraMetaMap.keySet()) {
                assertThat(extraMetaMap.get(key).equals((metadataJsonMap.get(key))))
                        .isTrue();
            }
        }
    }

    @Test
    void remove_all_from_store() throws SQLException {
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            float[] vector = randomVector(VECTOR_SIZE);
            embeddings.add(new Embedding(vector));
        }
        List<String> ids = store.addAll(embeddings);
        String stringIds = ids.stream().map(id -> String.format("'%s'", id)).collect(Collectors.joining(","));
        try (Statement statement = defaultConnection.createStatement(); ) {
            // assert IDs exist
            ResultSet rs = statement.executeQuery(String.format(
                    "SELECT \"%s\" FROM \"%s\" WHERE \"%s\" IN (%s)",
                    embeddingStoreConfig.getIdColumn(), TABLE_NAME, embeddingStoreConfig.getIdColumn(), stringIds));
            while (rs.next()) {
                String response = rs.getString(embeddingStoreConfig.getIdColumn());
                assertThat(ids).contains(response);
            }
        }

        store.removeAll(ids);

        try (Statement statement = defaultConnection.createStatement(); ) {
            // assert IDs were removed
            ResultSet rs = statement.executeQuery(String.format(
                    "SELECT \"%s\" FROM \"%s\" WHERE \"%s\" IN (%s)",
                    embeddingStoreConfig.getIdColumn(), TABLE_NAME, embeddingStoreConfig.getIdColumn(), stringIds));
            assertThat(rs.isBeforeFirst()).isFalse();
        }
    }

    @Test
    void search_for_vector_min_score_0() {
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> textSegments = new ArrayList<>();
        Map<Integer, Map<String, Object>> metaMaps = new HashMap<>();

        Stack<String> hayStack = new Stack<>();
        for (int i = 0; i < 10; i++) {
            float[] vector = randomVector(VECTOR_SIZE);
            embeddings.add(new Embedding(vector));
            Map<String, Object> metaMap = new HashMap<>();
            metaMap.put("string", "s" + i);
            metaMap.put("uuid", UUID.randomUUID());
            metaMap.put("integer", i);
            metaMap.put("long", 1L);
            metaMap.put("float", 1f);
            metaMap.put("double", 1d);
            metaMap.put("extra", "not in table columns " + i);
            metaMap.put("extra_credits", 100 + i);
            Metadata metadata = new Metadata(metaMap);
            textSegments.add(new TextSegment("this is a test text " + i, metadata));
            metaMaps.put(i, metaMap);

            hayStack.push("s" + i);
        }

        store.addAll(embeddings, textSegments);

        // filter by a column
        IsIn isIn = new IsIn("string", hayStack);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddings.get(1))
                .maxResults(10)
                .minScore(0.0)
                .filter(isIn)
                .build();

        List<EmbeddingMatch<TextSegment>> result = store.search(request).matches();

        // should return all 10
        assertThat(result.size()).isEqualTo(10);

        for (EmbeddingMatch<TextSegment> match : result) {
            Map<String, Object> matchMetadata = match.embedded().metadata().toMap();
            Integer index = (Integer) matchMetadata.get("integer");
            assertThat(match.embedded().text()).contains("this is a test text " + index);
            // metadata json should be unpacked into the original columns
            for (String column : matchMetadata.keySet()) {
                assertThat(matchMetadata.get(column))
                        .isEqualTo(metaMaps.get(index).get(column));
            }
        }
    }

    @Test
    void search_for_vector_specific_min_score_embedding_model() {
        List<String> testTexts = Arrays.asList("cat", "dog", "car", "truck");
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> textSegments = new ArrayList<>();

        for (String text : testTexts) {
            Map<String, Object> metaMap = new HashMap<>();
            metaMap.put("string", "s" + text);
            metaMap.put("uuid", UUID.randomUUID());
            metaMap.put("integer", 1);
            metaMap.put("long", 1L);
            metaMap.put("float", 1f);
            metaMap.put("double", 1d);
            metaMap.put("extra", "not in table columns ");
            metaMap.put("extra_credits", 100);
            Metadata metadata = new Metadata(metaMap);
            textSegments.add(new TextSegment(text, metadata));
            // using AllMiniLmL6V2QuantizedEmbeddingModel for consistency with other implementations
            embeddings.add(embeddingModel.embed(text).content());
        }

        store.addAll(embeddings, textSegments);
        // search for "cat"
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddings.get(0))
                .maxResults(2)
                .minScore(0.5)
                .build();

        List<EmbeddingMatch<TextSegment>> result = store.search(request).matches();
        // should get 2 hits
        assertThat(result.size()).isEqualTo(2);
        List<String> expectedSearchResult = Arrays.asList("cat", "dog");
        List<String> actualSearchResult = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result) {
            actualSearchResult.add(match.embedded().text());
        }
        assertThat(actualSearchResult).isEqualTo(expectedSearchResult);

        // search for "cat" using a higher minScore
        request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddings.get(0))
                .minScore(0.9)
                .build();

        result = store.search(request).matches();

        // should get 1 hit
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0).embedded().text()).isEqualTo("cat");
    }
}
