package dev.langchain4j.store.embedding.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreAddAllContract;
import dev.langchain4j.store.embedding.EmbeddingStoreRemoveAllContract;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryEmbeddingStoreContractTest implements EmbeddingStoreAddAllContract, EmbeddingStoreRemoveAllContract {

    @Override
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Test
    void should_add_embeddings_with_ids_without_embedded_contents() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        Embedding first = Embedding.from(new float[] {1.0f, 0.0f});
        Embedding second = Embedding.from(new float[] {0.0f, 1.0f});

        store.addAll(List.of("first", "second"), List.of(first, second), null);

        assertThat(store.entries)
                .containsExactly(
                        new InMemoryEmbeddingStore.Entry<>("first", first, null),
                        new InMemoryEmbeddingStore.Entry<>("second", second, null));
    }

    @Test
    void should_keep_existing_entries_when_no_embeddings_are_added() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        Embedding embedding = Embedding.from(new float[] {1.0f, 0.0f});
        TextSegment segment = TextSegment.from("existing");
        store.add("existing", embedding, segment);

        store.addAll(null, null, null);
        store.addAll(List.of(), List.of(), null);
        store.addAll(null, List.of(), List.of());
        store.addAll(List.of(), null, List.of());

        assertThat(store.entries).containsExactly(new InMemoryEmbeddingStore.Entry<>("existing", embedding, segment));
    }

    @Test
    void should_preserve_positions_when_embedded_contents_are_provided() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        Embedding first = Embedding.from(new float[] {1.0f, 0.0f});
        Embedding second = Embedding.from(new float[] {0.0f, 1.0f});
        TextSegment firstSegment = TextSegment.from("first segment");
        TextSegment secondSegment = TextSegment.from("second segment");

        store.addAll(List.of("first", "second"), List.of(first, second), List.of(firstSegment, secondSegment));

        assertThat(store.entries)
                .containsExactly(
                        new InMemoryEmbeddingStore.Entry<>("first", first, firstSegment),
                        new InMemoryEmbeddingStore.Entry<>("second", second, secondSegment));
    }
}
