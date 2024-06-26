package dev.langchain4j.model.azure;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.azure.AzureOpenAiEmbeddingModelName.TEXT_EMBEDDING_ADA_002;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

class AzureOpenAiEmbeddingModelIT {

    Logger logger = LoggerFactory.getLogger(AzureOpenAiEmbeddingModelIT.class);

    EmbeddingModel model = AzureOpenAiEmbeddingModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("text-embedding-ada-002")
            .tokenizer(new AzureOpenAiTokenizer(TEXT_EMBEDDING_ADA_002))
            .logRequestsAndResponses(true)
            .build();

    @Test
    void should_embed_and_return_token_usage() {

        Response<Embedding> response = model.embed("hello world");
        logger.info(response.toString());

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
        System.out.println(response);

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
            mode = EXCLUDE, names = "TEXT_EMBEDDING_ADA_002_2")
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
        System.out.println(response.toString());

        // then
        assertThat(response.content().vector()).isNotEmpty();
    }

    @Test
    void should_return_correct_dimension() {
        assertThat(model.dimension()).isEqualTo(1536);
    }
}
