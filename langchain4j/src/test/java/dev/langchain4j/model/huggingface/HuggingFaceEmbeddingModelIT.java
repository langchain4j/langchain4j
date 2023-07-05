package dev.langchain4j.model.huggingface;

import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class HuggingFaceEmbeddingModelIT {

    HuggingFaceEmbeddingModel model = HuggingFaceEmbeddingModel.builder()
            .accessToken(System.getenv("HF_API_KEY"))
            .modelId("sentence-transformers/all-MiniLM-L6-v2")
            .build();

    @Test
    void should_embed_one_text() {
        Result<Embedding> result = model.embed("hello");

        assertThat(result.get().vector()).hasSize(384);
    }

    @Test
    void should_embed_multiple_segments() {
        Result<List<Embedding>> result = model.embedAll(asList(
                DocumentSegment.from("hello"),
                DocumentSegment.from("how are you?")
        ));

        assertThat(result.get()).hasSize(2);
        assertThat(result.get().get(0).vector()).hasSize(384);
        assertThat(result.get().get(1).vector()).hasSize(384);
    }
}