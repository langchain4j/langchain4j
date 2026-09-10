package dev.langchain4j.store.embedding.milvus.v2;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
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
 * Verifies {@code containsString} against a real Milvus server. Asserting the generated LIKE expression
 * is not enough: a wildcard escaped with a single backslash produces an expression the server rejects
 * outright, which a string assertion cannot see.
 */
@Testcontainers
class MilvusV2ContainsStringIT {

    private static final String COLLECTION_NAME = "contains_string_test";

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
                .build();

        for (String tag : List.of("50%off", "50XXoff", "a_b", "axb", "back\\slash", "plain")) {
            TextSegment segment = TextSegment.from(tag, new Metadata().put("tag", tag));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
        awaitUntilAsserted(() -> assertThat(tagsMatching(metadataKey("tag").containsString("plain")))
                .containsExactly("plain"));
    }

    @AfterEach
    void afterEach() {
        embeddingStore.dropCollection(COLLECTION_NAME);
    }

    @Test
    void percent_in_value_should_be_matched_literally_and_not_as_a_wildcard() {
        assertThat(tagsMatching(metadataKey("tag").containsString("50%off"))).containsExactly("50%off");
    }

    @Test
    void underscore_in_value_should_be_matched_literally_and_not_as_a_wildcard() {
        assertThat(tagsMatching(metadataKey("tag").containsString("a_b"))).containsExactly("a_b");
    }

    @Test
    void underscore_should_match_every_value_that_contains_it() {
        assertThat(tagsMatching(metadataKey("tag").containsString("_"))).containsExactly("a_b");
    }

    @Test
    void backslash_in_value_should_be_matched_literally() {
        assertThat(tagsMatching(metadataKey("tag").containsString("back\\slash")))
                .containsExactly("back\\slash");
    }

    @Test
    void value_without_wildcards_should_match_as_a_substring() {
        assertThat(tagsMatching(metadataKey("tag").containsString("off")))
                .containsExactlyInAnyOrder("50%off", "50XXoff");
    }

    private List<String> tagsMatching(Filter filter) {
        return embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed("tag").content())
                        .filter(filter)
                        .maxResults(100)
                        .build())
                .matches()
                .stream()
                .map(EmbeddingMatch::embedded)
                .map(segment -> segment.metadata().getString("tag"))
                .sorted()
                .toList();
    }
}
