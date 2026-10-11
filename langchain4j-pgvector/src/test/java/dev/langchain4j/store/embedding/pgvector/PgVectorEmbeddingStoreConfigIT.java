package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
abstract class PgVectorEmbeddingStoreConfigIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    static EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    static DataSource dataSource;

    static final String TABLE_NAME = "test";
    static final int TABLE_DIMENSION = 384;

    static void configureStore(MetadataStorageConfig config) {
        var source = new PGSimpleDataSource();
        source.setServerNames(new String[] {pgVector.getHost()});
        source.setPortNumbers(new int[] {pgVector.getFirstMappedPort()});
        source.setDatabaseName("test");
        source.setUser("test");
        source.setPassword("test");
        dataSource = source;
        embeddingStore = PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(TABLE_NAME)
                .dimension(TABLE_DIMENSION)
                .dropTableFirst(true)
                .metadataStorageConfig(config)
                .build();
    }

    @BeforeEach
    void beforeEach() {
        try (var connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate("TRUNCATE TABLE %s".formatted(TABLE_NAME));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void ensureStoreIsEmpty() {
        // it's not necessary to clear the store before every test
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @Test
    void sqlInjectionShouldBePrevented() {

        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();

        Filter filter = metadataKey("key").isEqualTo("foo'; DROP TABLE " + TABLE_NAME + "; --");

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(1)
                .filter(filter)
                .build();

        try {
            embeddingStore().search(searchRequest);
        } catch (Exception e) {
            // ignore failure
        }

        // make sure table and embeddings are still there
        assertThat(getAllEmbeddings()).isNotEmpty();
    }

    @Test
    void sql_injection_via_metadata_key_should_be_prevented__search() {

        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();

        // A malicious metadata key that tries to break out of the JSON ->> literal (or the bare
        // column identifier) and turn the filter into an always-true condition (OR 1=1).
        Filter filter = metadataKey("x') IS NOT NULL OR 1=1 OR (metadata->>'y").isEqualTo("no-such-value");

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(10)
                .filter(filter)
                .build();

        List<EmbeddingMatch<TextSegment>> matches;
        try {
            matches = embeddingStore().search(searchRequest).matches();
        } catch (Exception e) {
            // a rejected/invalid key is acceptable - the injection did not succeed
            matches = Collections.emptyList();
        }

        // The malicious filter must not match the stored embedding.
        assertThat(matches).isEmpty();
    }

    @Test
    void sql_injection_via_metadata_key_should_be_prevented__remove() {

        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        // A malicious filter that, if injected, would turn the DELETE into an always-true
        // condition and wipe the whole table.
        Filter filter = metadataKey("x') IS NOT NULL OR 1=1 OR (metadata->>'y").isEqualTo("no-such-value");

        try {
            embeddingStore().removeAll(filter);
        } catch (Exception e) {
            // a rejected/invalid key is acceptable - the injection did not succeed
        }

        // The malicious filter must not have deleted the stored embedding.
        assertThat(getAllEmbeddings()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource
    void should_filter_fractional_values_by_integer_comparison_value(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        should_filter_by_metadata(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    /**
     * Numbers are compared by value whatever their Java type, as {@code Filter.test()} does: an integer
     * comparison value must work on a key holding fractional values, and {@code 20} must match a stored
     * {@code 20.0}.
     */
    static Stream<Arguments> should_filter_fractional_values_by_integer_comparison_value() {
        return Stream.of(
                Arguments.of(
                        metadataKey("age").isGreaterThan(30),
                        asList(new Metadata().put("age", 30.4), new Metadata().put("age", 40)),
                        asList(new Metadata().put("age", 30), new Metadata().put("age", 29.9))),
                Arguments.of(
                        metadataKey("age").isLessThanOrEqualTo(30L),
                        asList(new Metadata().put("age", 29.9), new Metadata().put("age", 30.0)),
                        asList(new Metadata().put("age", 30.4))),
                Arguments.of(
                        metadataKey("age").isEqualTo(20),
                        asList(new Metadata().put("age", 20.0), new Metadata().put("age", 20)),
                        asList(new Metadata().put("age", 20.5), new Metadata().put("age", 19.6))),
                Arguments.of(
                        metadataKey("age").isIn(20, 30),
                        asList(new Metadata().put("age", 20.0), new Metadata().put("age", 30)),
                        asList(new Metadata().put("age", 20.5), new Metadata().put("age", 25))),
                Arguments.of(
                        metadataKey("age").isNotIn(20L),
                        asList(new Metadata().put("age", 20.5), new Metadata().put("age", 25), new Metadata()),
                        asList(new Metadata().put("age", 20.0), new Metadata().put("age", 20))));
    }
}
