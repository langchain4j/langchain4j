package dev.langchain4j.model.huggingface;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class HuggingFaceEmbeddingModelIT {

    HuggingFaceEmbeddingModel model = HuggingFaceEmbeddingModel.builder()
            .accessToken(System.getenv("HF_API_KEY"))
            .modelId("sentence-transformers/all-MiniLM-L6-v2")
            .build();

    @Test
    void should_embed_one_text() {
        Embedding embedding = model.embed("hello").content();

        assertThat(embedding.vector()).hasSize(384);
    }

    @Test
    void should_embed_multiple_segments() {
        List<Embedding> embeddings = model.embedAll(asList(
                textSegment("hello"),
                textSegment("how are you?")
        )).content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).hasSize(384);
        assertThat(embeddings.get(1).vector()).hasSize(384);
    }
}