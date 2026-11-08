package dev.langchain4j.model.voyageai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VoyageAiEmbeddingModelNameTest {

    @Test
    void should_expose_voyage_context_4() {
        assertThat(VoyageAiEmbeddingModelName.VOYAGE_CONTEXT_4.toString()).isEqualTo("voyage-context-4");
        assertThat(VoyageAiEmbeddingModelName.VOYAGE_CONTEXT_4.dimension()).isEqualTo(1024);
        assertThat(VoyageAiEmbeddingModelName.knownDimension("voyage-context-4"))
                .isEqualTo(1024);
    }
}
