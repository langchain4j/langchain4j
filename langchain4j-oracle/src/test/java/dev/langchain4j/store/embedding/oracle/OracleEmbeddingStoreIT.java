package dev.langchain4j.store.embedding.oracle;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class OracleEmbeddingStoreIT {
    @Container
    private static final OracleContainer oracleContainer = new OracleContainer("gvenzl/oracle-free:23.4-slim-faststart")
            .withUsername("testuser")
            .withPassword(("testpwd"));
    private static OracleEmbeddingStore store;
    private static PoolDataSource dataSource;
    private static final String table = "vector_store";

    private static final EmbeddingWrapper w1 = EmbeddingWrapper.of("hello, world!");
    private static final EmbeddingWrapper w2 = EmbeddingWrapper.of("There are 50 states in the USA.")
            .kv("total", 50)
            .kv("contiguous", 48)
            .kv("territories", 16)
            .kv("government_type", "constitutional republic");
    private static final EmbeddingWrapper w3 = EmbeddingWrapper.of("The Hobbit is one of Tolkien's many works.")
            .kv("author", "J. R. R. Tolkien")
            .kv("age", 81);
    private static final EmbeddingWrapper w4 = EmbeddingWrapper.of("Langchain4j rocks!")
            .kv("opensource", "yes!")
            .kv("keyword", "ai");
    private static final EmbeddingWrapper w5 = EmbeddingWrapper.of("There are 10 provinces in Canada.")
            .kv("total", 10)
            .kv("territories", 3)
            .kv("government_type", "constitutional monarchy");

    @BeforeAll
    static void setup() throws SQLException {
        oracleContainer.start();
        dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("EMBEDDING_STORE_IT");
        dataSource.setUser(oracleContainer.getUsername());
        dataSource.setPassword(oracleContainer.getPassword());
        dataSource.setURL(oracleContainer.getJdbcUrl());
        store = OracleEmbeddingStore.builder()
            .dataSource(dataSource)
            .table(table)
            .dimension(384)
            .useIndex(true)
            .createTable(true)
            .dropTableFirst(true)
            .normalizeVectors(false)
            .build();
    }

    @Test
    void addBatch() {
        String tableName = "vector_table_batch";
        OracleEmbeddingStore embeddingStore = OracleEmbeddingStore.builder()
                .table(tableName)
                .dataSource(dataSource)
                .batchSize(2)
                .dimension(384)
                .useIndex(true)
                .createTable(true)
                .dropTableFirst(true)
                .normalizeVectors(false)
                .build();
        List<String> ids = embeddingStore.addAll(listOf(w1, w2, w3, w4, w5));
        for (String id : ids) {
            assertPresent(id, tableName);
        }
    }

    @Test
    void addAndRemove() {
        String id1 = store.add(w1.getEmbedding());
        assertThat(id1).isNotNull();
        store.remove(id1);
        String id2 = store.add(w2.getEmbedding());
        assertThat(id2).isNotNull();
        assertThat(id2).isNotEqualTo(id1);
    }

    @Test
    void addCollectionRemoveCollection() {
        List<String> ids = store.addAll(listOf(w1, w2, w3));
        store.removeAll(ids);
    }

    public static Stream<Arguments> addRemoveFilters() {
        return Stream.of(
                Arguments.of(w3.getEmbedding(), w3.getTextSegment(), MetadataFilterBuilder.metadataKey("author").isEqualTo("J. R. R. Tolkien")),
                Arguments.of(w3.getEmbedding(), w3.getTextSegment(), MetadataFilterBuilder.metadataKey("age").isEqualTo(81)),
                Arguments.of(w3.getEmbedding(), w3.getTextSegment(), MetadataFilterBuilder.metadataKey("age").isGreaterThan(50))
        );
    }

    @ParameterizedTest
    @MethodSource("addRemoveFilters")
    void addRemoveWithFilter(Embedding embedding, TextSegment textSegment, Filter filter) {
        String id = store.add(embedding, textSegment);
        assertPresent(id, table);
        store.removeAll(filter);
        assertNotPresent(id, table);
    }

    @Test
    void addWithId() {
        store.add(UUID.randomUUID().toString(), w2.getEmbedding());
    }

    @Test
    void addWithSegment() {
        String id3 = store.add(w3.getEmbedding(), w3.getTextSegment());
        assertThat(id3).isNotNull();
    }

    public static Stream<Arguments> searchArgs() {
        return Stream.of(
                Arguments.of(OracleEmbeddingStore.DistanceType.COSINE, null),
                Arguments.of(OracleEmbeddingStore.DistanceType.DOT, null),
                Arguments.of(OracleEmbeddingStore.DistanceType.COSINE, 75),
                Arguments.of(OracleEmbeddingStore.DistanceType.DOT, 75)
        );
    }

    @ParameterizedTest
    @MethodSource("searchArgs")
    void searchDistanceTypes(OracleEmbeddingStore.DistanceType distanceType, Integer accuracy) {
        OracleEmbeddingStore embeddingStore = OracleEmbeddingStore.builder()
            .dataSource(dataSource)
            .table("vector_store_2")
            .dimension(384)
            .distanceType(distanceType)
            .dropTableFirst(true)
            .normalizeVectors(true)
            .build();
        addWrappers(embeddingStore, w1, w2, w3, w4);

        // Assert we can find embeddings that exist
        EmbeddingWrapper searchEmbedding = EmbeddingWrapper.of("Langchain4j");
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(searchEmbedding.getEmbedding())
                .maxResults(1)
                .minScore(0.7)
                .build());
        List<EmbeddingMatch<TextSegment>> matches = result.matches();
        assertThat(matches).hasSize(1);
        EmbeddingMatch<TextSegment> match = matches.get(0);
        assertThat(match.embedded().text()).isEqualTo(w4.getTextSegment().text());
        assertThat(match.embedded().metadata().getString("keyword")).isEqualTo("ai");

        // Assert
        EmbeddingWrapper searchEmbedding2 = EmbeddingWrapper.of("ERROR");
        EmbeddingSearchResult<TextSegment> result2 = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(searchEmbedding2.getEmbedding())
                .maxResults(1)
                .minScore(0.7)
                .build());
        List<EmbeddingMatch<TextSegment>> matches2 = result2.matches();
        assertThat(matches2).hasSize(0);
    }

    public static Stream<Arguments> searchFilters() {
        return Stream.of(
                Arguments.of(OracleEmbeddingStore.DistanceType.COSINE, Filter.not(MetadataFilterBuilder.metadataKey("government_type").isEqualTo("constitutional monarchy"))),
                Arguments.of(OracleEmbeddingStore.DistanceType.DOT, MetadataFilterBuilder.metadataKey("government_type").isNotIn("constitutional monarchy", "xyz")),
                Arguments.of(OracleEmbeddingStore.DistanceType.DOT, MetadataFilterBuilder.metadataKey("government_type").isIn("constitutional republic", "xyz")),
                Arguments.of(OracleEmbeddingStore.DistanceType.DOT, MetadataFilterBuilder.metadataKey("government_type").isNotEqualTo("constitutional monarchy")),
                Arguments.of(OracleEmbeddingStore.DistanceType.COSINE, MetadataFilterBuilder.metadataKey("government_type").isEqualTo("constitutional republic")),
                Arguments.of(OracleEmbeddingStore.DistanceType.DOT, MetadataFilterBuilder.metadataKey("territories").isGreaterThan(1)),
                Arguments.of(OracleEmbeddingStore.DistanceType.COSINE, MetadataFilterBuilder.metadataKey("total").isLessThan(100))
        );
    }

    @ParameterizedTest
    @MethodSource("searchFilters")
    void searchWithFilter(OracleEmbeddingStore.DistanceType distanceType, Filter filter) {
        OracleEmbeddingStore embeddingStore = OracleEmbeddingStore.builder()
            .dataSource(dataSource)
            .table("vector_store_3")
            .dimension(384)
            .distanceType(distanceType)
            .dropTableFirst(true)
            .normalizeVectors(true)
            .build();
        addWrappers(embeddingStore, w1, w2, w3, w4, w5);

        EmbeddingWrapper searchEmbedding = EmbeddingWrapper.of("USA");
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(searchEmbedding.getEmbedding())
                .minScore(0.0)
                .filter(filter)
                .maxResults(1)
                .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().text()).isEqualTo(w2.getTextSegment().text());
    }

    public static Stream<Arguments> searchAccuracy() {
        return Stream.of(
                Arguments.of()
        );
    }

    private void addWrappers(OracleEmbeddingStore store, EmbeddingWrapper... wrapper) {
        for (EmbeddingWrapper w : wrapper) {
            store.add(w.getEmbedding(), w.getTextSegment());
        }
    }

    private List<Embedding> listOf(EmbeddingWrapper... es) {
        return Arrays.stream(es)
                .map(EmbeddingWrapper::getEmbedding)
                .collect(Collectors.toList());
    }

    private void assertPresent(String id, String tableName) {
        assertPresence(id, tableName, true);
    }

    private void assertNotPresent(String id, String tableName) {
        assertPresence(id, tableName,false);
    }

    private void assertPresence(String id, String tableName, boolean exists) {
        try (Connection connection = dataSource.getConnection(); Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(String.format("select * from %s where id = '%s'", tableName, id));
            assertThat(rs.isBeforeFirst()).isEqualTo(exists);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
