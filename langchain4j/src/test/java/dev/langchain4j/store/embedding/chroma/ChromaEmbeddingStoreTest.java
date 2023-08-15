package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

class ChromaEmbeddingStoreTest {

    @Test
    @Disabled("To run this test, you must have a local Chroma instance")
    public void testAddEmbeddingAndFindRelevant() {
        ChromaEmbeddingStore chromaEmbeddingStore = new ChromaEmbeddingStore("http://localhost:8000",
                "default", ofSeconds(15));

        Embedding embedding = Embedding.from(new float[]{9.9F, 4.5F, 3.5F, 1.3F, 1.7F, 5.7F, 6.4F, 5.5F, 8.2F, 9.3F, 1.5F});
        TextSegment textSegment = TextSegment.textSegment("Text", Metadata.from("Key", "Value"));
        String added = chromaEmbeddingStore.add(embedding, textSegment);
        assertThat(added).isNotBlank();

        Embedding refereceEmbedding = Embedding.from(new float[]{8.7F, 4.5F, 3.4F, 1.2F, 5.5F, 5.6F, 6.4F, 5.5F, 8.1F, 9.1F, 1.1F});
        List<EmbeddingMatch<TextSegment>> embeddingMatches = chromaEmbeddingStore.findRelevant(refereceEmbedding, 10);
        assertThat(embeddingMatches.size()).isEqualTo(1);
    }

}