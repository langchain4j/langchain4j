package dev.langchain4j.store.embedding.mariadb;

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
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbPoolDataSource;
import org.testcontainers.containers.MariaDBContainer;

abstract class MariaDbEmbeddingStoreConfigIT extends EmbeddingStoreWithFilteringIT {
    static MariaDBContainer<?> mariadbContainer = MariaDbTestUtils.defaultContainer;

    static EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    static MariaDbPoolDataSource dataSource;

    static final String TABLE_NAME = "test";
    static final int TABLE_DIMENSION = 384;

    static void configureStore(MetadataStorageConfig config) {
        mariadbContainer.start();
        String jdbcUrl = "%s?useBulkStmtsForInserts=false&connectionCollation=utf8mb4_bin&user=%s&password=%s&allowMultiQueries=true"
                .formatted(
                        mariadbContainer.getJdbcUrl(), mariadbContainer.getUsername(), mariadbContainer.getPassword());
        try {
            dataSource = new MariaDbPoolDataSource(jdbcUrl);
            embeddingStore = MariaDbEmbeddingStore.builder()
                    .datasource(dataSource)
                    .table(TABLE_NAME)
                    .dimension(TABLE_DIMENSION)
                    .createTable(true)
                    .dropTableFirst(true)
                    .metadataStorageConfig(config)
                    .build();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    static void afterAll() {
        dataSource.close();
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
    protected boolean testFloatExactly() {
        return false;
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

        // A malicious metadata key that tries to break out of the JSON path / identifier
        // and turn the filter into an always-true condition (OR 1=1).
        Filter filter = metadataKey("nonexistent') OR 1=1 OR JSON_VALUE(metadata, '$.x")
                .isEqualTo("no-such-value");

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(10)
                .filter(filter)
                .build();

        List<EmbeddingMatch<TextSegment>> matches;
        try {
            matches = embeddingStore().search(searchRequest).matches();
        } catch (Exception e) {
            // a rejected/invalid identifier is acceptable - the injection did not succeed
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
        Filter filter = metadataKey("nonexistent') OR 1=1 OR JSON_VALUE(metadata, '$.x")
                .isEqualTo("no-such-value");

        try {
            embeddingStore().removeAll(filter);
        } catch (Exception e) {
            // a rejected/invalid identifier is acceptable - the injection did not succeed
        }

        // The malicious filter must not have deleted the stored embedding.
        assertThat(getAllEmbeddings()).hasSize(1);
    }

    @Test
    void sql_injection_via_metadata_value_with_backslash_should_be_prevented() {

        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();

        // MariaDB treats backslash as an escape character by default, so simply doubling the
        // single quote is not enough: a trailing backslash turns the escaped quote ('') back
        // into an escaped quote followed by a string terminator, breaking out of the value
        // literal and injecting an always-true "OR 1=1".
        Filter filter = metadataKey("category").isEqualTo("a\\' OR 1=1 -- ");

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(10)
                .filter(filter)
                .build();

        List<EmbeddingMatch<TextSegment>> matches;
        try {
            matches = embeddingStore().search(searchRequest).matches();
        } catch (Exception e) {
            // a rejected value is acceptable - the injection did not succeed
            matches = Collections.emptyList();
        }

        // The malicious value must not match the stored embedding.
        assertThat(matches).isEmpty();
    }

    @Test
    void sql_injection_via_metadata_key_with_null_byte_should_be_prevented() {

        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();

        // In COLUMN_PER_KEY mode the key is quoted as an identifier. A key that cannot be quoted
        // because it contains a NUL character used to fall back to the raw, unescaped key, which
        // the injected "OR 1=1" turns into an always-true condition ("-- " comments out the rest).
        Filter filter = metadataKey("key IS NOT NULL OR 1=1 -- \0").isEqualTo("v");

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(10)
                .filter(filter)
                .build();

        List<EmbeddingMatch<TextSegment>> matches;
        try {
            matches = embeddingStore().search(searchRequest).matches();
        } catch (Exception e) {
            // a rejected identifier is acceptable - the injection did not succeed
            matches = Collections.emptyList();
        }

        // The malicious key must not match the stored embedding.
        assertThat(matches).isEmpty();
    }

    @Test
    void should_bind_map_value_as_parameter__remove() {

        addThreeTenants();

        // A loosely typed value, e.g. a JSON object that Jackson parsed into a Map. Its toString() starts
        // with "{", which the driver would read as a JDBC escape sequence if it were part of the SQL text.
        embeddingStore().removeAll(unvalidatedIsEqualTo("name", Map.of("fn 1} or 1=1 -- ", "")));
        embeddingStore().removeAll(unvalidatedIsIn("name", Map.of("fn 1}) or 1=1 -- ", "")));

        assertThat(getAllEmbeddings()).hasSize(3);

        embeddingStore().removeAll(metadataKey("name").isIn("doc-a"));

        assertThat(names(getAllEmbeddings())).containsExactlyInAnyOrder("doc-b", "doc-c");
    }

    @Test
    void should_bind_nested_collection_value_as_parameter__remove() {

        addThreeTenants();

        // regression guard: nested collections were already handled safely before values were bound
        embeddingStore().removeAll(unvalidatedIsIn("name", List.of("x') or 1=1 --")));

        assertThat(getAllEmbeddings()).hasSize(3);
    }

    @Test
    void should_bind_char_sequence_value_as_parameter__remove() {

        addThreeTenants();

        embeddingStore().removeAll(unvalidatedIsEqualTo("name", new StringBuilder("1 or 1=1")));
        embeddingStore().removeAll(unvalidatedIsIn("name", new StringBuilder("1) or (1=1")));

        assertThat(getAllEmbeddings()).hasSize(3);
    }

    @Test
    void should_bind_string_value_with_quotes_as_parameter__remove() {

        addThreeTenants();

        embeddingStore().removeAll(metadataKey("name").isIn("x') or 1=1 -- "));
        embeddingStore().removeAll(metadataKey("name").isEqualTo("x\\' or 1=1 -- "));

        assertThat(getAllEmbeddings()).hasSize(3);
    }

    @Test
    void should_keep_other_conditions_for_char_sequence_value__search() {

        addThreeTenants();

        Filter filter = metadataKey("city")
                .isEqualTo("tenant-a")
                .and(unvalidatedIsIn("name", new StringBuilder("'x') or 1=1 or ('x'")));

        assertThat(search(filter)).isEmpty();

        Filter validFilter =
                metadataKey("city").isEqualTo("tenant-a").and(metadataKey("name").isIn("doc-a", "doc-b"));

        assertThat(names(search(validFilter))).containsExactly("doc-a");
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
