package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStoreIT.milvusContainer;
import static io.milvus.common.clientenum.ConsistencyLevelEnum.STRONG;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

@Testcontainers
class MilvusEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Container
    static MilvusContainer milvus = milvusContainer();

    MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
            .uri(milvus.getEndpoint())
            .collectionName("test_collection_" + randomUUID().replace("-", ""))
            .username(System.getenv("MILVUS_USERNAME"))
            .password(System.getenv("MILVUS_PASSWORD"))
            .consistencyLevel(STRONG)
            .dimension(384)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Test
    void should_also_remove_entries_without_the_key_when_removing_by_not_equal() {
        TextSegment withA = TextSegment.from("k is a", new Metadata().put("k", "a"));
        TextSegment withB = TextSegment.from("k is b", new Metadata().put("k", "b"));
        TextSegment withoutK = TextSegment.from("no k", new Metadata().put("other", "x"));
        for (TextSegment segment : List.of(withA, withB, withoutK)) {
            embeddingStore().add(embeddingModel().embed(segment).content(), segment);
        }
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(3));

        embeddingStore().removeAll(metadataKey("k").isNotEqualTo("a"));

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));
        assertThat(getAllEmbeddings().get(0).embedded().metadata().getString("k"))
                .isEqualTo("a");
    }
}
