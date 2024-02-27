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

        Metadata metadata = new Metadata();
        metadata.put("string", "abc");
        metadata.put("integer", 1);
        metadata.put("long", 2L);
        metadata.put("float", 3.3f);
        metadata.put("double", 4.4d);

        TextSegment segment = TextSegment.from("hello", metadata);
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

        assertThat(match.embedded().text()).isEqualTo(segment.text());

        assertThat(match.embedded().metadata().getString("string")).isEqualTo("abc");
        assertThat(match.embedded().metadata().getInteger("integer")).isEqualTo(1);
        assertThat(match.embedded().metadata().getLong("long")).isEqualTo(2L);
        assertThat(match.embedded().metadata().getFloat("float")).isEqualTo(3.3f);
        assertThat(match.embedded().metadata().getDouble("double")).isEqualTo(4.4d);

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build()).matches()).isEqualTo(relevant);
    }
}
