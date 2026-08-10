package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleGenAiEmbeddingModelTest {

    @Test
    void exposes_provider_supported_parameters_and_listeners() {
        EmbeddingModelListener listener = new EmbeddingModelListener() {};

        GoogleGenAiEmbeddingModel model = GoogleGenAiEmbeddingModel.builder()
                .modelName("gemini-embedding-001")
                .apiKey("dummy")
                .listeners(List.of(listener))
                .build();

        assertThat(model.provider()).isEqualTo(ModelProvider.GOOGLE_GENAI);
        assertThat(model.listeners()).containsExactly(listener);
        assertThat(model.supportedParameters())
                .containsExactlyInAnyOrder(
                        EmbeddingRequestParameters.INPUT_TYPE, EmbeddingRequestParameters.DIMENSIONS);
    }
}
