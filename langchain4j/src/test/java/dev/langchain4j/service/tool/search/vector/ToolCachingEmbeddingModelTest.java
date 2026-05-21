package dev.langchain4j.service.tool.search.vector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCachingEmbeddingModelTest {

    @Test
    void shouldCacheToolEmbeddingsButNeverCacheQuery() {

        // given
        RecordingEmbeddingModel delegate = new RecordingEmbeddingModel();
        ToolCachingEmbeddingModel cachingModel = new ToolCachingEmbeddingModel(delegate);

        TextSegment query = TextSegment.from("search query");
        TextSegment tool1 = TextSegment.from("tool description 1");
        TextSegment tool2 = TextSegment.from("tool description 2");

        List<TextSegment> segments = List.of(query, tool1, tool2);

        // when – first call
        Response<List<Embedding>> firstResponse = cachingModel.embedAll(segments);

        // then
        assertThat(firstResponse.content()).hasSize(3);
        assertThat(delegate.embedAllCalls()).isEqualTo(1);
        assertThat(delegate.embeddedTexts())
                .containsExactly("search query", "tool description 1", "tool description 2");

        // when – second call with the same input
        delegate.reset();
        Response<List<Embedding>> secondResponse = cachingModel.embedAll(segments);

        // then
        assertThat(secondResponse.content()).hasSize(3);

        // only query should be embedded again
        assertThat(delegate.embedAllCalls()).isEqualTo(1);
        assertThat(delegate.embeddedTexts()).containsExactly("search query");
    }

    @Test
    void shouldClearCache() {

        // given
        RecordingEmbeddingModel delegate = new RecordingEmbeddingModel();
        ToolCachingEmbeddingModel cachingModel = new ToolCachingEmbeddingModel(delegate);

        TextSegment query = TextSegment.from("query");
        TextSegment tool = TextSegment.from("tool");

        List<TextSegment> segments = List.of(query, tool);

        // warm up cache
        cachingModel.embedAll(segments);
        delegate.reset();

        // when
        cachingModel.clearCache();
        cachingModel.embedAll(segments);

        // then – tool must be embedded again after cache clear
        assertThat(delegate.embedAllCalls()).isEqualTo(1);
        assertThat(delegate.embeddedTexts()).containsExactly("query", "tool");
    }

    /**
     * Simple fake EmbeddingModel that records calls and inputs.
     */
    static class RecordingEmbeddingModel implements EmbeddingModel {

        private final AtomicInteger embedAllCalls = new AtomicInteger();
        private final List<String> embeddedTexts = new ArrayList<>();

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            embedAllCalls.incrementAndGet();

            List<Embedding> embeddings = new ArrayList<>();
            for (TextSegment segment : textSegments) {
                embeddedTexts.add(segment.text());
                embeddings.add(new Embedding(new float[]{1, 2, 3, 4, 5}));
            }

            return Response.from(embeddings);
        }

        int embedAllCalls() {
            return embedAllCalls.get();
        }

        List<String> embeddedTexts() {
            return List.copyOf(embeddedTexts);
        }

        void reset() {
            embedAllCalls.set(0);
            embeddedTexts.clear();
        }

        @Override
        public Response<Embedding> embed(String text) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int dimension() {
            return 1;
        }

        @Override
        public String modelName() {
            return "recording-model";
        }
    }
}