package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class EmbeddingModelTest implements WithAssertions {
    public static class EmbeddingModelImpl implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings =
            textSegments.stream().map(ts -> new Embedding(new float[]{ts.text().length(), ts.text().hashCode()}))
                    .collect(Collectors.toList());

            int tokenUsage = textSegments.stream().mapToInt(ts -> ts.text().length()).sum();

            return Response.from(embeddings, new TokenUsage(tokenUsage), FinishReason.STOP);
        }
    }

    public static class BrokenEmbeddingModelImpl implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

            List<TextSegment> doubledList = new ArrayList<>(textSegments);
            doubledList.addAll(textSegments);

            return new EmbeddingModelImpl().embedAll(doubledList);
        }
    }

    @Test
    public void test() {
        EmbeddingModel model = new EmbeddingModelImpl();

        String abcDef = "abc def";
        Response<Embedding> response =  model.embed(abcDef);

        assertThat(response.content().vector()).containsExactly(abcDef.length(), abcDef.hashCode());
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(abcDef.length()));
    }

    @Test
    public void test_broken() {
        EmbeddingModel model = new BrokenEmbeddingModelImpl();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> model.embed("abc def"))
                .withMessageContaining("Expected a single embedding, but got 2");
    }
}