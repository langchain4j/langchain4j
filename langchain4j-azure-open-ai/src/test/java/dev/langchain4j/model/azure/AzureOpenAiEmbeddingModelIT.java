package dev.langchain4j.model.azure;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

class AzureOpenAiEmbeddingModelIT {

    EmbeddingModel model = AzureOpenAiEmbeddingModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("text-embedding-3-small")
            .logRequestsAndResponses(true)
            .build();

    @Test
    void should_embed_and_return_token_usage() {

        Response<Embedding> response = model.embed("hello world");

        assertThat(response.content().vector()).hasSize(1536);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_in_batches() {

        int batchSize = 16;
        int numberOfSegments = batchSize + 1;

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < numberOfSegments; i++) {
            segments.add(TextSegment.from("text " + i));
        }

        Response<List<Embedding>> response = model.embedAll(segments);

        assertThat(response.content()).hasSize(numberOfSegments);
        assertThat(response.content().get(0).dimension()).isEqualTo(1536);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(numberOfSegments * 3);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(numberOfSegments * 3);

        assertThat(response.finishReason()).isNull();
    }

    @ParameterizedTest(name = "Testing model {0}")
    @EnumSource(value = AzureOpenAiEmbeddingModelName.class,
            mode = EXCLUDE, names = {"TEXT_EMBEDDING_ADA_002_2", "TEXT_EMBEDDING_3_SMALL", "TEXT_EMBEDDING_3_SMALL_1", "TEXT_EMBEDDING_3_LARGE", "TEXT_EMBEDDING_ADA_002", "TEXT_EMBEDDING_ADA_002_1"})
    void should_support_all_string_model_names(AzureOpenAiEmbeddingModelName modelName) {

        // given
        String modelNameString = modelName.toString();

        EmbeddingModel model = AzureOpenAiEmbeddingModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(modelNameString)
                .logRequestsAndResponses(true)
                .build();

        // when
        Response<Embedding> response = model.embed("hello world");

        // then
        assertThat(response.content().vector()).isNotEmpty();
    }

    @Test
    void should_embed_text_with_embedding_shortening() {

        // given
        int dimensions = 100;

        EmbeddingModel model = AzureOpenAiEmbeddingModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName("text-embedding-3-small")
                .dimensions(dimensions)
                .logRequestsAndResponses(true)
                .build();

        // when
        Response<Embedding> response = model.embed("hello world");

        // then
        assertThat(response.content().dimension()).isEqualTo(dimensions);
    }

    @Test
    void should_return_correct_dimension() {
        assertThat(model.dimension()).isEqualTo(1536);
    }
}
