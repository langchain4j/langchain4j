package dev.langchain4j.store.embedding.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InMemoryEmbeddingStoreTest extends EmbeddingStoreWithFilteringIT {

    @TempDir
    Path temporaryDirectory;

    EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Test
    void should_serialize_to_and_deserialize_from_json() {

        InMemoryEmbeddingStore<TextSegment> originalEmbeddingStore = createEmbeddingStore();

        String json = originalEmbeddingStore.serializeToJson();
        InMemoryEmbeddingStore<TextSegment> deserializedEmbeddingStore = InMemoryEmbeddingStore.fromJson(json);

        assertThat(deserializedEmbeddingStore.entries)
                .isEqualTo(originalEmbeddingStore.entries)
                .isInstanceOf(CopyOnWriteArrayList.class);
    }

    @Test
    void should_serialize_to_and_deserialize_from_file() {
        InMemoryEmbeddingStore<TextSegment> originalEmbeddingStore = createEmbeddingStore();
        Path filePath = temporaryDirectory.resolve("embedding-store.json");

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(
                        () -> originalEmbeddingStore.serializeToFile(temporaryDirectory.resolve("missing/store.json")))
                .withCauseInstanceOf(NoSuchFileException.class);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> InMemoryEmbeddingStore.fromFile(temporaryDirectory.resolve("missing/store.json")))
                .withCauseInstanceOf(NoSuchFileException.class);
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> InMemoryEmbeddingStore.fromFile(
                        temporaryDirectory.resolve("missing/store.json").toString()))
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

            assertThat(deserializedEmbeddingStore.entries)
                    .isEqualTo(originalEmbeddingStore.entries)
                    .isInstanceOf(CopyOnWriteArrayList.class);
        }
    }

    @Test
    void should_merge_multiple_stores() {

        // given
        InMemoryEmbeddingStore<TextSegment> store1 = new InMemoryEmbeddingStore<>();
        TextSegment segment1 = TextSegment.from("first", Metadata.from("first-key", "first-value"));
        Embedding embedding1 = embeddingModel.embed(segment1).content();
        store1.add("1", embedding1, segment1);

        InMemoryEmbeddingStore<TextSegment> store2 = new InMemoryEmbeddingStore<>();
        TextSegment segment2 = TextSegment.from("second", Metadata.from("second-key", "second-value"));
        Embedding embedding2 = embeddingModel.embed(segment2).content();
        store2.add("2", embedding2, segment2);

        // when
        InMemoryEmbeddingStore<TextSegment> merged = InMemoryEmbeddingStore.merge(store1, store2);

        // then
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding1)
                .maxResults(100)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = merged.search(searchRequest).matches();
        assertThat(matches).hasSize(2);

        assertThat(matches.get(0).embeddingId()).isEqualTo("1");
        assertThat(matches.get(0).embedding()).isEqualTo(embedding1);
        assertThat(matches.get(0).embedded()).isEqualTo(segment1);

        assertThat(matches.get(1).embeddingId()).isEqualTo("2");
        assertThat(matches.get(1).embedding()).isEqualTo(embedding2);
        assertThat(matches.get(1).embedded()).isEqualTo(segment2);
    }

    private InMemoryEmbeddingStore<TextSegment> createEmbeddingStore() {

        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        assertThat(embeddingStore.size()).isEqualTo(0);
        assertThat(embeddingStore.isEmpty()).isEqualTo(true);

        TextSegment segment = TextSegment.from("first");
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);

        assertThat(embeddingStore.size()).isEqualTo(1);
        assertThat(embeddingStore.isEmpty()).isEqualTo(false);

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

    @Override
    protected boolean supportsContains() {
        return true;
    }
}
