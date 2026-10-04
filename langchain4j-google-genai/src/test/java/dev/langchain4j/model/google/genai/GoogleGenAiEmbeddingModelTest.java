package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void should_reject_non_positive_max_segments_per_batch(int maxSegmentsPerBatch) {
        assertThatThrownBy(() -> modelWithMaxSegmentsPerBatch(maxSegmentsPerBatch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxSegmentsPerBatch must be greater than zero, but is: " + maxSegmentsPerBatch);
    }

    @Test
    void should_accept_positive_max_segments_per_batch() {
        assertThatNoException().isThrownBy(() -> modelWithMaxSegmentsPerBatch(1));
    }

    private static GoogleGenAiEmbeddingModel modelWithMaxSegmentsPerBatch(int maxSegmentsPerBatch) {
        return GoogleGenAiEmbeddingModel.builder()
                .modelName("gemini-embedding-001")
                .apiKey("dummy")
                .maxSegmentsPerBatch(maxSegmentsPerBatch)
                .build();
    }
}
