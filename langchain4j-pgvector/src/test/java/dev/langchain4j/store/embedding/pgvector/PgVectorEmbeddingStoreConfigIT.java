package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
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
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @Test
    void should_bind_nested_collection_value_as_parameter__remove() {

        addThreeTenants();

        // A loosely typed value, e.g. a list inside the list that Jackson parsed into a List<Object>.
        // It is bound as one text value, so it matches no row.
        embeddingStore().removeAll(unvalidatedIsIn("name", List.of("x') or 1=1 --")));

        assertThat(getAllEmbeddings()).hasSize(3);

        embeddingStore().removeAll(metadataKey("name").isIn("doc-a"));

        assertThat(names(getAllEmbeddings())).containsExactlyInAnyOrder("doc-b", "doc-c");
    }

    @Test
    void should_bind_char_sequence_value_as_parameter__remove() {

        addThreeTenants();

        embeddingStore().removeAll(unvalidatedIsIn("name", new StringBuilder("x') or 1=1 --")));
        // there is no SQL type to cast the key to for a StringBuilder, so the database rejects this filter
        removeAllIgnoringFailure(unvalidatedIsEqualTo("name", new StringBuilder("x') or 1=1 --")));

        assertThat(getAllEmbeddings()).hasSize(3);
    }

    @Test
    void should_bind_string_value_with_quotes_as_parameter__remove() {

        addThreeTenants();

        embeddingStore().removeAll(metadataKey("name").isIn("x') or 1=1 --"));
        embeddingStore().removeAll(metadataKey("name").isEqualTo("x') or 1=1 --"));

        assertThat(getAllEmbeddings()).hasSize(3);
    }

    @Test
    void should_keep_other_conditions_for_nested_collection_value__search() {

        addThreeTenants();

        Filter filter = metadataKey("city")
                .isEqualTo("tenant-a")
                .and(unvalidatedIsIn("name", List.of("x') or 1=1 or ('a'='a")));

        assertThat(search(filter)).isEmpty();

        Filter validFilter =
                metadataKey("city").isEqualTo("tenant-a").and(metadataKey("name").isIn("doc-a", "doc-b"));

        assertThat(names(search(validFilter))).containsExactly("doc-a");
    }

    @Test
    void should_compare_byte_short_big_integer_and_big_decimal_values_with_stored_numbers() {

        List<TextSegment> segments = Stream.of(1, 2, 3)
                .map(i -> TextSegment.from(
                        "text " + i,
                        new Metadata().put("name", "doc-" + i).put("integer_1", i).put("age", i + 0.5)))
                .toList();
        embeddingStore().addAll(embeddingModel().embedAll(segments).content(), segments);

        assertThat(names(search(new IsGreaterThan("integer_1", (byte) 1)))).containsExactlyInAnyOrder("doc-2", "doc-3");
        assertThat(names(search(new IsLessThan("integer_1", (short) 3)))).containsExactlyInAnyOrder("doc-1", "doc-2");
        assertThat(names(search(new IsEqualTo("integer_1", new BigInteger("2"))))).containsExactly("doc-2");
        assertThat(names(search(new IsGreaterThan("age", new BigDecimal("2.0")))))
                .containsExactlyInAnyOrder("doc-2", "doc-3");
        assertThat(names(search(new IsEqualTo("age", new BigDecimal("2.5"))))).containsExactly("doc-2");
    }

    private void addThreeTenants() {
        List<TextSegment> segments = Stream.of("a", "b", "c")
                .map(tenant -> TextSegment.from(
                        "text of tenant " + tenant,
                        new Metadata().put("city", "tenant-" + tenant).put("name", "doc-" + tenant)))
                .toList();
        List<Embedding> embeddings = embeddingModel().embedAll(segments).content();
        embeddingStore().addAll(embeddings, segments);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(3));
    }

    private void removeAllIgnoringFailure(Filter filter) {
        try {
            embeddingStore().removeAll(filter);
        } catch (Exception e) {
            // the database may reject this filter; it must not remove anything either way
        }
    }

    private List<EmbeddingMatch<TextSegment>> search(Filter filter) {
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel().embed("text").content())
                .maxResults(10)
                .filter(filter)
                .build();
        return embeddingStore().search(searchRequest).matches();
    }

    private static List<String> names(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream()
                .map(match -> match.embedded().metadata().getString("name"))
                .toList();
    }

    // The filter classes reject values like these, so they are returned from the overridden accessor
    // to check that the store handles them safely on its own.
    private static IsEqualTo unvalidatedIsEqualTo(String key, Object value) {
        return new IsEqualTo(key, "placeholder") {
            @Override
            public Object comparisonValue() {
                return value;
            }
        };
    }

    private static IsIn unvalidatedIsIn(String key, Object... values) {
        return new IsIn(key, List.of("placeholder")) {
            @Override
            public Collection<?> comparisonValues() {
                return List.of(values);
            }
        };
    }
}
