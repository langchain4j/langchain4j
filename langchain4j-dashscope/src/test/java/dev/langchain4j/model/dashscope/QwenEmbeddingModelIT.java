package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static dev.langchain4j.model.dashscope.QwenTestHelper.apiKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenEmbeddingModelIT {

    private EmbeddingModel getModel(String modelName) {
        return QwenEmbeddingModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_one_text(String modelName) {
        EmbeddingModel model = getModel(modelName);
        Embedding embedding = model.embed("hello").content();

        assertThat(embedding.vector()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_documents(String modelName) {
        EmbeddingModel model = getModel(modelName);
        List<Embedding> embeddings = model.embedAll(asList(
                textSegment("hello"),
                textSegment("how are you?")
        )).content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).isNotEmpty();
        assertThat(embeddings.get(1).vector()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_queries(String modelName) {
        EmbeddingModel model = getModel(modelName);
        List<Embedding> embeddings = model.embedAll(asList(
                textSegment("hello", Metadata.from(QwenEmbeddingModel.TYPE_KEY, QwenEmbeddingModel.TYPE_QUERY)),
                textSegment("how are you?", Metadata.from(QwenEmbeddingModel.TYPE_KEY, QwenEmbeddingModel.TYPE_QUERY))
        )).content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).isNotEmpty();
        assertThat(embeddings.get(1).vector()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_mix_segments(String modelName) {
        EmbeddingModel model = getModel(modelName);
        List<Embedding> embeddings = model.embedAll(asList(
                textSegment("hello", Metadata.from(QwenEmbeddingModel.TYPE_KEY, QwenEmbeddingModel.TYPE_QUERY)),
                textSegment("how are you?")
        )).content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).isNotEmpty();
        assertThat(embeddings.get(1).vector()).isNotEmpty();
    }
}
