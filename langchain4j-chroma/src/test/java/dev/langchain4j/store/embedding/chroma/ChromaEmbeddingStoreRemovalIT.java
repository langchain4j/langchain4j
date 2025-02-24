package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.filter.Filter.and;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ChromaEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @Container
    private static final ChromaDBContainer chroma = new ChromaDBContainer("chromadb/chroma:0.5.4");

    EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore.builder()
            .baseUrl(chroma.getEndpoint())
            .collectionName(randomUUID())
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
    void should_remove_by_not_equals() {
        // given
        TextSegment segment1 = TextSegment.from("matching", metadata("type", "a"));
        embeddingStore().add(embeddingModel().embed(segment1).content(), segment1);

        TextSegment segment2 = TextSegment.from("matching", metadata("type", "a"));
        embeddingStore().add(embeddingModel().embed(segment2).content(), segment2);

        TextSegment segment3 = TextSegment.from("not matching", metadata("type", "b"));
        String id3 = embeddingStore().add(embeddingModel().embed(segment3).content(), segment3);

        // when
        embeddingStore().removeAll(metadataKey("type").isNotEqualTo("b"));

        // then
        List<EmbeddingMatch<TextSegment>> relevant = getAllEmbeddings();
        assertThat(relevant).hasSize(1);
        assertThat(relevant.get(0).embeddingId()).isEqualTo(id3);
    }

    @Test
    void should_remove_by_is_greater_than_with_and() {
        // given
        TextSegment segment1 = TextSegment.from("not matching", new Metadata().put("cat", 10));
        String id1 = embeddingStore().add(embeddingModel().embed(segment1).content(), segment1);

        TextSegment segment2 =
                TextSegment.from("matching", new Metadata().put("cat", 15).put("type", "a"));
        embeddingStore().add(embeddingModel().embed(segment2).content(), segment2);

        TextSegment segment3 = TextSegment.from("not matching", new Metadata().put("cat", 1));
        String id3 = embeddingStore().add(embeddingModel().embed(segment3).content(), segment3);

        // when
        embeddingStore()
                .removeAll(and(
                        metadataKey("cat").isGreaterThan(5), metadataKey("type").isEqualTo("a")));

        // then
        List<EmbeddingMatch<TextSegment>> relevant = getAllEmbeddings();
        assertThat(relevant).hasSize(2);
        assertThat(relevant.get(0).embeddingId()).isEqualTo(id1);
        assertThat(relevant.get(1).embeddingId()).isEqualTo(id3);
    }
}
