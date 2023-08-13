package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.time.Duration.ofSeconds;

//To run this test, you must have a local Chroma instance
@Ignore
class ChromaEmbeddingStoreTest {

    private final ChromaEmbeddingStore chromaEmbeddingStore = new ChromaEmbeddingStore("http://localhost:8000",
            ofSeconds(15), "collection45");

    @Test
    public void testAddEmbedding() {
        Embedding embedding = Embedding.from(new float[]{9.9F, 4.5F, 3.5F, 1.3F, 1.7F, 5.7F, 6.4F, 5.5F, 8.2F, 9.3F, 1.5F});
        TextSegment textSegment = TextSegment.textSegment("Text", Metadata.from("Key", "Value"));
        String added = chromaEmbeddingStore.add(embedding, textSegment);
        System.out.println(added);

        Embedding refereceEmbedding = Embedding.from(new float[]{8.7F, 4.5F, 3.4F, 1.2F, 5.5F, 5.6F, 6.4F, 5.5F, 8.1F, 9.1F, 1.1F});
        List<EmbeddingMatch<TextSegment>> embeddingMatches = chromaEmbeddingStore.findRelevant(refereceEmbedding, 10);
        embeddingMatches.forEach(System.out::println);
    }

    @Test
    public void testGetNearestNeighbour() {
        Embedding refereceEmbedding = Embedding.from(new float[]{1.5F, 2.9F, 3.4F, 1.2F, 1.5F, 5.6F, 1.4F, 5.5F, 8.1F, 9.1F, 1.1F});
        List<EmbeddingMatch<TextSegment>> embeddingMatches = chromaEmbeddingStore.findRelevant(refereceEmbedding, 2);
        embeddingMatches.forEach(System.out::println);
    }

}