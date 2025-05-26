package dev.langchain4j.model.openai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.model.openai.OpenAiEmbeddingModelName.TEXT_EMBEDDING_3_SMALL;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiEmbeddingModelIT {

    EmbeddingModel model = OpenAiEmbeddingModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(TEXT_EMBEDDING_3_SMALL)
            .logRequests(true)
            .logResponses(false) // embeddings are huge in logs
            .build();

    @Test
    void should_embed_single_text() {

        // given
        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);

        // then
        assertThat(response.content().vector()).hasSize(1536);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_multiple_segments() {

        // given
        List<TextSegment> segments = asList(
                TextSegment.from("hello"),
                TextSegment.from("world")
        );

        // when
        Response<List<Embedding>> response = model.embedAll(segments);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).dimension()).isEqualTo(1536);
        assertThat(response.content().get(1).dimension()).isEqualTo(1536);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_multiple_batch_segments() {
        // given
        int maxSegmentsPerBatch = 10;
        int totalSegmentsToEmbed = 50;

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(TEXT_EMBEDDING_3_SMALL)
                .maxSegmentsPerBatch(maxSegmentsPerBatch)
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        List<TextSegment> segments = Stream.generate(() -> TextSegment.from("hello"))
                .limit(totalSegmentsToEmbed)
                .toList();

        // when
        Response<List<Embedding>> response = model.embedAll(segments);

        // then
        assertThat(response.content()).hasSize(totalSegmentsToEmbed);
        assertThat(response.content().get(0).dimension()).isEqualTo(1536);
        assertThat(response.content().get(10).dimension()).isEqualTo(1536);
        assertThat(response.content().get(20).dimension()).isEqualTo(1536);
        assertThat(response.content().get(30).dimension()).isEqualTo(1536);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(totalSegmentsToEmbed);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(totalSegmentsToEmbed);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_text_with_embedding_shortening() {

        // given
        int dimension = 42;

        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(TEXT_EMBEDDING_3_SMALL)
                .dimensions(dimension)
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();

        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);

        // then
        assertThat(response.content().dimension()).isEqualTo(dimension);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);

        assertThat(response.finishReason()).isNull();
    }
}
