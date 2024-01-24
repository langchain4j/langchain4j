package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

/**
 * A minimum set of tests that each implementation of {@link EmbeddingStore} must pass.
 */
public abstract class EmbeddingStoreIT extends EmbeddingStoreWithoutMetadataIT {

    @Test
    void should_add_embedding_with_segment_with_metadata() {

        TextSegment segment = TextSegment.from("hello", Metadata.from("test-key", "test-value"));
        Embedding embedding = embeddingModel().embed(segment.text()).content();

        String id = embeddingStore().add(embedding, segment);
        assertThat(id).isNotBlank();

        {
            // Not returned.
            TextSegment altSegment = TextSegment.from("hello?");
            Embedding altEmbedding = embeddingModel().embed(altSegment.text()).content();
            embeddingStore().add(altEmbedding, segment);
        }

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 1);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);
    }
}
