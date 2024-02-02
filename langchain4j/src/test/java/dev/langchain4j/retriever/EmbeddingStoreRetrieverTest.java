package dev.langchain4j.retriever;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class EmbeddingStoreRetrieverTest implements WithAssertions {
    private final EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Test
    public void test_constructors() {
        EmbeddingStoreRetriever actual = new EmbeddingStoreRetriever(embeddingStore, embeddingModel, 2, null);
        assertThat(actual.getEmbeddingStore()).isEqualTo(embeddingStore);
        assertThat(actual.getEmbeddingModel()).isEqualTo(embeddingModel);
        assertThat(actual.getMaxResults()).isEqualTo(2);
        assertThat(actual.getMinScore()).isEqualTo(0.0);

        assertThat(actual)
                .isEqualTo(actual)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isNotEqualTo(new EmbeddingStoreRetriever(embeddingStore, embeddingModel, 3, 9.0))
                .hasSameHashCodeAs(EmbeddingStoreRetriever.from(embeddingStore, embeddingModel, 2, null))
                .isEqualTo(EmbeddingStoreRetriever.from(embeddingStore, embeddingModel, 2, null))
                .isEqualTo(EmbeddingStoreRetriever.from(embeddingStore, embeddingModel, 2))
                .isEqualTo(EmbeddingStoreRetriever.from(embeddingStore, embeddingModel));

        EmbeddingStoreRetriever r = new EmbeddingStoreRetriever(embeddingStore, embeddingModel, 2, 9.0);
        assertThat(r.getMinScore()).isEqualTo(9.0);
    }

    @Test
    public void test_findRelevant() {
        EmbeddingStoreRetriever retriever = new EmbeddingStoreRetriever(embeddingStore, embeddingModel, 2, null);

        List<TextSegment> relevant = retriever.findRelevant("banana milkshake");
        assertThat(relevant).isEmpty();

        TextSegment ts = TextSegment.from("banana");
        embeddingStore.add(embeddingModel.embed(ts).content(), ts);

        relevant = retriever.findRelevant("banana milkshake");
        assertThat(relevant).containsExactly(ts);
    }
}