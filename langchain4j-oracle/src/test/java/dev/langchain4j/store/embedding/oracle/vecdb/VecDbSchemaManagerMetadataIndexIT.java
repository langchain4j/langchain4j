package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the VecDB metadata-index lifecycle selected for an existing vector table.
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("supportMetadataIndex")
class VecDbSchemaManagerMetadataIndexIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TABLE_NAME = "LC4J_VECDB_METADATA_INDEX_IT";
    private static final String INITIAL_PATH = "category";
    private static final String UPDATED_PATH = "tenant";
    private static final Embedding TEST_EMBEDDING = new Embedding(new float[] {1.0f, 0.0f, 0.0f});
    private static final TextSegment TEST_SEGMENT = TextSegment.from(
            "Metadata index test",
            new Metadata().put(INITIAL_PATH, "documentation").put(UPDATED_PATH, "langchain4j"));

    @BeforeEach
    void createTableWithoutMetadataIndex() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
        OracleVecDbEmbeddingStore embeddingStore =
                createStore(CreateOption.CREATE_IF_NOT_EXISTS, metadataIndex(CreateOption.CREATE_NONE, INITIAL_PATH));
        embeddingStore.add(TEST_EMBEDDING, TEST_SEGMENT);
    }

    @AfterEach
    void removeTestTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }

    /** Verifies that metadata-index {@code CREATE_NONE} leaves a missing index absent. */
    @Test
    void testCreateNoneDoesNotCreateMissingIndex() throws SQLException {
        createStore(CreateOption.CREATE_NONE, metadataIndex(CreateOption.CREATE_NONE, UPDATED_PATH));

        assertThat(metadataIndexExists()).isFalse();
    }

    /** Verifies that metadata-index {@code CREATE_NONE} preserves an existing index. */
    @Test
    void testCreateNoneKeepsExistingIndex() throws Exception {
        createStore(CreateOption.CREATE_NONE, metadataIndex(CreateOption.CREATE_IF_NOT_EXISTS, INITIAL_PATH));

        createStore(CreateOption.CREATE_NONE, metadataIndex(CreateOption.CREATE_NONE, UPDATED_PATH));

        assertThat(metadataIndexExists()).isTrue();
        assertThat(metadataIncludePaths()).containsExactly(INITIAL_PATH);
    }

    /** Verifies that {@code CREATE_IF_NOT_EXISTS} creates a missing metadata index. */
    @Test
    void testCreateIfNotExistsCreatesMissingIndex() throws Exception {
        createStore(CreateOption.CREATE_NONE, metadataIndex(CreateOption.CREATE_IF_NOT_EXISTS, INITIAL_PATH));

        assertThat(metadataIndexExists()).isTrue();
        assertThat(metadataIncludePaths()).containsExactly(INITIAL_PATH);
    }

    /** Verifies that {@code CREATE_IF_NOT_EXISTS} keeps existing metadata paths unchanged. */
    @Test
    void testCreateIfNotExistsKeepsExistingIndex() throws Exception {
        createStore(CreateOption.CREATE_NONE, metadataIndex(CreateOption.CREATE_IF_NOT_EXISTS, INITIAL_PATH));

        createStore(CreateOption.CREATE_NONE, metadataIndex(CreateOption.CREATE_IF_NOT_EXISTS, UPDATED_PATH));

        assertThat(metadataIndexExists()).isTrue();
        assertThat(metadataIncludePaths()).containsExactly(INITIAL_PATH);
    }

    /** Verifies that {@code CREATE_OR_REPLACE} creates a metadata index when none exists. */
    @Test
    void testCreateOrReplaceCreatesMissingIndex() throws Exception {
        createStore(CreateOption.CREATE_NONE, metadataIndex(CreateOption.CREATE_OR_REPLACE, INITIAL_PATH));

        assertThat(metadataIndexExists()).isTrue();
        assertThat(metadataIncludePaths()).containsExactly(INITIAL_PATH);
    }

    /** Verifies that {@code CREATE_OR_REPLACE} replaces an existing metadata index configuration. */
    @Test
    void testCreateOrReplaceReplacesExistingIndex() throws Exception {
        createStore(CreateOption.CREATE_NONE, metadataIndex(CreateOption.CREATE_IF_NOT_EXISTS, INITIAL_PATH));

        createStore(CreateOption.CREATE_NONE, metadataIndex(CreateOption.CREATE_OR_REPLACE, UPDATED_PATH));

        assertThat(metadataIndexExists()).isTrue();
        assertThat(metadataIncludePaths()).containsExactly(UPDATED_PATH);
    }

    private static OracleVecDbEmbeddingStore createStore(
            CreateOption tableCreateOption, VecDbMetadataIndex metadataIndex) {
        return OracleVecDbEmbeddingStore.builder()
                .dataSource(VecDbTestOperations.dataSource())
                .embeddingTable(TABLE_NAME, tableCreateOption)
                .metadataIndex(metadataIndex)
                .distanceMetric(VecDbDistanceMetric.COSINE)
                .build();
    }

    private static VecDbMetadataIndex metadataIndex(CreateOption createOption, String includePath) {
        return VecDbMetadataIndex.builder()
                .createOption(createOption)
                .autoIndex(false)
                .includePath(includePath)
                .build();
    }

    private static boolean metadataIndexExists() throws SQLException {
        return VecDbTestOperations.indexStatus(TABLE_NAME).metadataIndexExists();
    }

    private static List<String> metadataIncludePaths() throws SQLException, JsonProcessingException {
        JsonNode description = OBJECT_MAPPER.readTree(VecDbTestOperations.describeVectorTable(TABLE_NAME));
        JsonNode includePaths =
                description.path("index_params").path("metadata_index_params").path("include_paths");

        List<String> paths = new ArrayList<>(includePaths.size());
        includePaths.forEach(path -> paths.add(path.asText()));
        return paths;
    }

    private static boolean supportMetadataIndex() {
        return VecDbTestOperations.apiVersion() == VecDbApiVersion.V23_26_3;
    }
}
