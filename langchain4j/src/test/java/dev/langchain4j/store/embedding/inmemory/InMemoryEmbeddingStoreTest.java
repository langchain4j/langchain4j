package dev.langchain4j.store.embedding.inmemory;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEmbeddingStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void should_add_and_find_relevant() {

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        Embedding embedding1 = Embedding.from(new float[]{1, 3});
        TextSegment segment1 = TextSegment.from("first");
        embeddingStore.add(embedding1, segment1);

        Embedding embedding2 = Embedding.from(new float[]{2, 2});
        TextSegment segment2 = TextSegment.from("second");
        String id2 = embeddingStore.add(embedding2, segment2);

        Embedding embedding3 = Embedding.from(new float[]{3, 1});
        TextSegment segment3 = TextSegment.from("third");
        String id3 = embeddingStore.add(embedding3, segment3);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(Embedding.from(new float[]{4, 0}), 2);

        assertThat(relevant).containsExactly(
                new EmbeddingMatch<>(0.9743416490252569, id3, embedding3, segment3),
                new EmbeddingMatch<>(0.8535533905932737, id2, embedding2, segment2)
        );
    }

    @Test
    void should_serialize_to_and_deserialize_from_json() {

        InMemoryEmbeddingStore<TextSegment> originalEmbeddingStore = createEmbeddingStore();

        String json = originalEmbeddingStore.serializeToJson();
        InMemoryEmbeddingStore<TextSegment> deserializedEmbeddingStore = InMemoryEmbeddingStore.fromJson(json);

        assertThat(deserializedEmbeddingStore).isEqualTo(originalEmbeddingStore);
    }

    @Test
    void should_serialize_to_and_deserialize_from_file() {

        InMemoryEmbeddingStore<TextSegment> originalEmbeddingStore = createEmbeddingStore();
        Path filePath = temporaryDirectory.resolve("embedding-store.json");

        originalEmbeddingStore.serializeToFile(filePath);
        InMemoryEmbeddingStore<TextSegment> deserializedEmbeddingStore = InMemoryEmbeddingStore.fromFile(filePath);

        assertThat(deserializedEmbeddingStore).isEqualTo(originalEmbeddingStore);
    }

    private static InMemoryEmbeddingStore<TextSegment> createEmbeddingStore() {

        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        Embedding embedding1 = Embedding.from(new float[]{1, 3});
        TextSegment segment1 = TextSegment.from("first");
        embeddingStore.add(embedding1, segment1);

        Embedding embedding2 = Embedding.from(new float[]{2, 2});
        TextSegment segment2 = TextSegment.from("second");
        embeddingStore.add(embedding2, segment2);

        Embedding embedding3 = Embedding.from(new float[]{3, 1});
        TextSegment segment3 = TextSegment.from("third");
        embeddingStore.add(embedding3, segment3);

        return embeddingStore;
    }
}
