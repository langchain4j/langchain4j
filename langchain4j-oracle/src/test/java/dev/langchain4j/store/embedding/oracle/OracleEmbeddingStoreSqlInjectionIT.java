package dev.langchain4j.store.embedding.oracle;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a crafted metadata key cannot inject SQL into the SELECT or DELETE statements. This mirrors the
 * end-to-end tests added for langchain4j-pgvector and langchain4j-mariadb (GHSA-2mfg-cc43-9pcj).
 */
public class OracleEmbeddingStoreSqlInjectionIT {

    // A key that, if concatenated unescaped into JSON_VALUE(metadata, '$.<key>' ...), turns the filter into an
    // always-true condition (OR 1=1), bypassing it to read or delete every row.
    private static final String MALICIOUS_KEY =
            "tenant' RETURNING NUMBER NULL ON ERROR) = 1 OR 1=1 OR JSON_VALUE(metadata, '$.ignored";

    private final OracleEmbeddingStore embeddingStore = CommonTestOperations.newEmbeddingStore();

    @BeforeEach
    void clearTable() {
        embeddingStore.removeAll();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        CommonTestOperations.dropTable();
    }

    private EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    private EmbeddingModel embeddingModel() {
        return CommonTestOperations.getEmbeddingModel();
    }

    private List<EmbeddingMatch<TextSegment>> allEmbeddings() {
        Embedding reference = embeddingModel().embed("reference").content();
        return embeddingStore()
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(reference)
                        .maxResults(100)
                        .build())
                .matches();
    }

    @Test
    void sql_injection_via_metadata_key_should_be_prevented__search() {
        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(embedding);
        assertThat(allEmbeddings()).hasSize(1);

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();
        Filter filter = metadataKey(MALICIOUS_KEY).isEqualTo(1);

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
        assertThat(allEmbeddings()).hasSize(1);

        Filter filter = metadataKey(MALICIOUS_KEY).isEqualTo(1);

        try {
            embeddingStore().removeAll(filter);
        } catch (Exception e) {
            // a rejected/invalid key is acceptable - the injection did not succeed
        }

        // The malicious filter must not have deleted the stored embedding.
        assertThat(allEmbeddings()).hasSize(1);
    }

    @Test
    void metadata_key_with_json_path_metacharacters_is_matched_literally() {
        // A key containing JSON path metacharacters ('.', '*'). Because the key is embedded as a quoted member name,
        // these are treated literally, so the key resolves to the top-level metadata field of exactly that name.
        String key = "a.b*";

        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(embedding, TextSegment.from("text", new Metadata().put(key, "value")));
        assertThat(allEmbeddings()).hasSize(1);

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();
        Filter filter = metadataKey(key).isEqualTo("value");

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore()
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(referenceEmbedding)
                        .maxResults(10)
                        .filter(filter)
                        .build())
                .matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().metadata().getString(key)).isEqualTo("value");
    }
}
