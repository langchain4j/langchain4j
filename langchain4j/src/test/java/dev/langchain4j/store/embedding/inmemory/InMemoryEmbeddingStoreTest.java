package dev.langchain4j.store.embedding.inmemory;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class InMemoryEmbeddingStoreTest extends EmbeddingStoreIT {

    @TempDir
    Path temporaryDirectory;

    EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Test
    void should_serialize_to_and_deserialize_from_json() {

        InMemoryEmbeddingStore<TextSegment> originalEmbeddingStore = createEmbeddingStore();

        String json = originalEmbeddingStore.serializeToJson();
        InMemoryEmbeddingStore<TextSegment> deserializedEmbeddingStore = InMemoryEmbeddingStore.fromJson(json);

        assertThat(deserializedEmbeddingStore.entries).isEqualTo(originalEmbeddingStore.entries);
        assertThat(deserializedEmbeddingStore.entries).isInstanceOf(CopyOnWriteArrayList.class);
    }

    @Test
    void should_serialize_to_and_deserialize_from_file() {
        InMemoryEmbeddingStore<TextSegment> originalEmbeddingStore = createEmbeddingStore();
        Path filePath = temporaryDirectory.resolve("embedding-store.json");

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> originalEmbeddingStore
                        .serializeToFile(temporaryDirectory.resolve("missing/store.json")))
                .withCauseInstanceOf(NoSuchFileException.class);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> InMemoryEmbeddingStore
                        .fromFile(temporaryDirectory.resolve("missing/store.json")))
                .withCauseInstanceOf(NoSuchFileException.class);
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> InMemoryEmbeddingStore
                        .fromFile(temporaryDirectory.resolve("missing/store.json").toString()))
                .withCauseInstanceOf(NoSuchFileException.class);

        {
            originalEmbeddingStore.serializeToFile(filePath);
            InMemoryEmbeddingStore<TextSegment> deserializedEmbeddingStore = InMemoryEmbeddingStore.fromFile(filePath);

            assertThat(deserializedEmbeddingStore.entries)
                    .isEqualTo(originalEmbeddingStore.entries)
                            .hasSameHashCodeAs(originalEmbeddingStore.entries);
            assertThat(deserializedEmbeddingStore.entries).isInstanceOf(CopyOnWriteArrayList.class);
        }
        {
            originalEmbeddingStore.serializeToFile(filePath.toString());
            InMemoryEmbeddingStore<TextSegment> deserializedEmbeddingStore = InMemoryEmbeddingStore.fromFile(filePath);

            assertThat(deserializedEmbeddingStore.entries).isEqualTo(originalEmbeddingStore.entries);
            assertThat(deserializedEmbeddingStore.entries).isInstanceOf(CopyOnWriteArrayList.class);
        }
    }

    private InMemoryEmbeddingStore<TextSegment> createEmbeddingStore() {

        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        TextSegment segment = TextSegment.from("first");
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);

        TextSegment segmentWithMetadata = TextSegment.from("second", Metadata.from("key", "value"));
        Embedding embedding2 = embeddingModel.embed(segmentWithMetadata).content();
        embeddingStore.add(embedding2, segmentWithMetadata);

        return embeddingStore;
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
