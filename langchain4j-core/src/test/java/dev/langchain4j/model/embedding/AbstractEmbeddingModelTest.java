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

class AbstractEmbeddingModelTest implements WithAssertions {

    public static class EmbeddingModelImpl extends AbstractEmbeddingModel {

        Map<String, Integer> dimensionMap;

        EmbeddingModelImpl() {

        }

        EmbeddingModelImpl(Integer dimension) {
            this.dimension = dimension;
        }

        EmbeddingModelImpl(Map<String, Integer> dimensionMap) {
            this.dimensionMap = dimensionMap;
        }

        @Override
        protected Map<String, Integer> dimensionMap() {
            return dimensionMap == null ? new HashMap<>() : dimensionMap;
        }

        @Override
        protected String modelName() {
            return "test-model";
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
        EmbeddingModel model = new EmbeddingModelImpl();
        assertThat(model.dimension()).isEqualTo(2);

        // twice call model.dimension() should use cache result
        assertThat(model.dimension()).isEqualTo(2);
    }

    @Test
    void should_return_init_dimension() {
        // init class with dimension
        EmbeddingModel model = new EmbeddingModelImpl(5);
        assertThat(model.dimension()).isEqualTo(5);
    }

    @Test
    void should_return_dimension_from_cached_map() {
        Map<String, Integer> dimensionMap = new HashMap<>();
        dimensionMap.put("test-model", 6);

        EmbeddingModel model = new EmbeddingModelImpl(dimensionMap);
        assertThat(model.dimension()).isEqualTo(6);
    }
}
