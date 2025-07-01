package dev.langchain4j.model.github;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.github.GitHubModelsEmbeddingModelName.TEXT_EMBEDDING_3_SMALL;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
class GitHubModelsEmbeddingModelIT {

    EmbeddingModel model = GitHubModelsEmbeddingModel.builder()
            .gitHubToken(System.getenv("GITHUB_TOKEN"))
            .modelName(TEXT_EMBEDDING_3_SMALL)
            .logRequestsAndResponses(false) // embeddings are huge in logs
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
    @EnumSource(value = GitHubModelsEmbeddingModelName.class)
    void should_support_all_string_model_names(GitHubModelsEmbeddingModelName modelName) {

        // given
        EmbeddingModel model = GitHubModelsEmbeddingModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(modelName.toString())
                .logRequestsAndResponses(false) // embeddings are huge in logs
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

        EmbeddingModel model = GitHubModelsEmbeddingModel.builder()
                .gitHubToken(System.getenv("GITHUB_TOKEN"))
                .modelName(TEXT_EMBEDDING_3_SMALL)
                .dimensions(dimensions)
                .logRequestsAndResponses(false) // embeddings are huge in logs
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
