package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class DimensionAwareEmbeddingModelTest implements WithAssertions {

    public static class DimensionAwareEmbeddingModelImpl extends DimensionAwareEmbeddingModel {

        final String modelName;

        DimensionAwareEmbeddingModelImpl(String modelName) {
            this.modelName = modelName;
        }

        DimensionAwareEmbeddingModelImpl(String modelName,
                                         Integer dimension) {
            this.modelName = modelName;
            this.dimension = dimension;
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings =
                    textSegments.stream().map(ts -> new Embedding(new float[]{ts.text().length(), ts.text().hashCode()}))
                            .collect(Collectors.toList());

            int tokenUsage = textSegments.stream().mapToInt(ts -> ts.text().length()).sum();

            return Response.from(embeddings, new TokenUsage(tokenUsage), FinishReason.STOP);
        }
    }

    @Test
    void should_return_correct_dimension_and_cached() {
        EmbeddingModel model = new DimensionAwareEmbeddingModelImpl("test-model");
        assertThat(model.dimension()).isEqualTo(2);

        // twice call model.dimension() should use cache result
        assertThat(model.dimension()).isEqualTo(2);
    }

    @Test
    void should_return_init_dimension() {
        // init class with dimension
        EmbeddingModel model = new DimensionAwareEmbeddingModelImpl("test-model", 5);
        assertThat(model.dimension()).isEqualTo(5);
    }
}
