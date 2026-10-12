package dev.langchain4j.store.embedding.milvus.v2;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.v2.common.ConsistencyLevel;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

/**
 * Verifies on a real Milvus server that a metadata filter key is escaped rather than interpreted as
 * filter expression syntax. Asserting only the generated expression string is not enough: it does not
 * prove the server accepts the expression, nor that a crafted key cannot widen the filter.
 */
@Testcontainers
class MilvusV2MetadataKeyEscapingIT {

    private static final String COLLECTION_NAME = "key_escaping_test";

    /** A key that closes the metadata accessor and appends a self-true term. */
    private static final String INJECTED_KEY = "tenant\"] != \"\" or metadata_field[\"x";

    @Container
    private static final MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.6.11")
            .withEnv("DEPLOY_MODE", "STANDALONE")
            .withEnv("MILVUS_MODE", "standalone")
            .withEnv("ETCD_USE_EMBED", "true")
            .withEnv("COMMON_STORAGETYPE", "local")
            .withCommand("milvus", "run", "standalone");

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private MilvusV2EmbeddingStore embeddingStore;

    @BeforeEach
    void beforeEach() {
        embeddingStore = MilvusV2EmbeddingStore.builder()
                .uri(milvus.getEndpoint())
                .collectionName(COLLECTION_NAME)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .dimension(384)
                .metadataFieldName("metadata_field")
                .build();

        add("acme doc", new Metadata().put("tenant", "acme"));
        add("globex doc", new Metadata().put("tenant", "globex"));
        add("quoted key doc", new Metadata().put("a\"b", "v"));
        add("backslash key doc", new Metadata().put("a\\b", "v"));

        awaitUntilAsserted(() ->
                assertThat(search(metadataKey("tenant").isEqualTo("acme"))).hasSize(1));
    }

    @AfterEach
    void afterEach() {
        embeddingStore.dropCollection(COLLECTION_NAME);
    }

    @Test
    void should_match_only_the_intended_tenant() {
        assertThat(search(metadataKey("tenant").isEqualTo("acme"))).hasSize(1);
    }

    @Test
    void should_round_trip_a_key_containing_a_double_quote() {
        assertThat(search(metadataKey("a\"b").isEqualTo("v"))).hasSize(1);
    }

    @Test
    void should_round_trip_a_key_containing_a_backslash() {
        assertThat(search(metadataKey("a\\b").isEqualTo("v"))).hasSize(1);
    }

    @Test
    void should_not_widen_the_filter_when_key_contains_expression_syntax() {
        // The injected term must be treated as part of the key, not as an "or" branch,
        // so the search matches nothing instead of returning the whole collection.
        assertThat(search(metadataKey(INJECTED_KEY).isEqualTo("irrelevant"))).isEmpty();
    }

    @Test
    void should_not_remove_anything_when_key_contains_expression_syntax() {
        embeddingStore.removeAll(metadataKey(INJECTED_KEY).isEqualTo("irrelevant"));

        awaitUntilAsserted(() ->
                assertThat(search(metadataKey("tenant").isEqualTo("acme"))).hasSize(1));
        assertThat(search(metadataKey("tenant").isEqualTo("globex"))).hasSize(1);
    }

    private void add(String text, Metadata metadata) {
        TextSegment segment = TextSegment.from(text, metadata);
        embeddingStore.add(embeddingModel.embed(segment).content(), segment);
    }

    private List<?> search(Filter filter) {
        return embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed("doc").content())
                        .filter(filter)
                        .maxResults(100)
                        .build())
                .matches();
    }
}
