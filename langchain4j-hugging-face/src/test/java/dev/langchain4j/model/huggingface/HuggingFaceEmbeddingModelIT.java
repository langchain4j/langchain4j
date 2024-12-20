package dev.langchain4j.model.huggingface;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "HF_API_KEY", matches = ".+")
class HuggingFaceEmbeddingModelIT {

    HuggingFaceEmbeddingModel model = HuggingFaceEmbeddingModel.builder()
            .accessToken(System.getenv("HF_API_KEY"))
            .modelId("sentence-transformers/all-MiniLM-L6-v2")
            .waitForModel(true)
            .build();

    HuggingFaceEmbeddingModel customUrlModel = HuggingFaceEmbeddingModel.builder()
            .baseUrl("https://api-inference.huggingface.co/")
            .accessToken(System.getenv("HF_API_KEY"))
            .modelId("sentence-transformers/all-MiniLM-L6-v2")
            .waitForModel(true)
            .build();

    @Test
    void should_embed_one_text() {
        Embedding embedding = model.embed("hello").content();

        assertThat(embedding.vector()).hasSize(384);
    }

    @Test
    void should_embed_multiple_segments() {
        List<Embedding> embeddings = model.embedAll(asList(textSegment("hello"), textSegment("how are you?")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).hasSize(384);
        assertThat(embeddings.get(1).vector()).hasSize(384);
    }

    @Test
    void custom_model_should_embed_one_text() {
        Embedding embedding = customUrlModel.embed("hello").content();

        assertThat(embedding.vector()).hasSize(384);
    }

    @Test
    void custom_model_should_embed_multiple_segments() {
        List<Embedding> embeddings = customUrlModel
                .embedAll(asList(textSegment("hello"), textSegment("how are you?")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).hasSize(384);
        assertThat(embeddings.get(1).vector()).hasSize(384);
    }

    @Test
    void should_fail_when_baseUrl_is_not_valid() {
        assertThatThrownBy(() -> {
                    HuggingFaceEmbeddingModel.builder()
                            .baseUrl("//not-valid/")
                            .accessToken(System.getenv("HF_API_KEY"))
                            .build();
                })
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected URL scheme 'http' or 'https'");
    }
}
