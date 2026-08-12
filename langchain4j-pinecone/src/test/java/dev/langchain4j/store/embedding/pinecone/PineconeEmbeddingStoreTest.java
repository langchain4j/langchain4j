package dev.langchain4j.store.embedding.pinecone;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import dev.langchain4j.data.embedding.Embedding;
import java.util.List;
import org.junit.jupiter.api.Test;

class PineconeEmbeddingStoreTest {

    private static Embedding embedding() {
        return Embedding.from(new float[] {1.0f, 2.0f, 3.0f});
    }

    @Test
    void addAll_should_throw_when_ids_size_differs_from_embeddings_size() {
        PineconeEmbeddingStore store =
                mock(PineconeEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        List<String> ids = List.of("id-1", "id-2");
        List<Embedding> embeddings = List.of(embedding(), embedding(), embedding());

        assertThatThrownBy(() -> store.addAll(ids, embeddings, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids size is not equal to embeddings size");
    }

    @Test
    void addAll_should_throw_when_textSegments_size_differs_from_embeddings_size() {
        PineconeEmbeddingStore store =
                mock(PineconeEmbeddingStore.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

        List<String> ids = List.of("id-1", "id-2");
        List<Embedding> embeddings = List.of(embedding(), embedding());
        List<dev.langchain4j.data.segment.TextSegment> textSegments =
                List.of(dev.langchain4j.data.segment.TextSegment.from("only-one"));

        assertThatThrownBy(() -> store.addAll(ids, embeddings, textSegments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddings size is not equal to textSegments size");
    }
}
