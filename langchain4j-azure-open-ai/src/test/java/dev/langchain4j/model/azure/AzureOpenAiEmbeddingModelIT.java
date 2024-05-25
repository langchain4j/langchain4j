package dev.langchain4j.model.azure;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.azure.AzureOpenAiModelName.*;
import static org.assertj.core.api.Assertions.assertThat;

public class AzureOpenAiEmbeddingModelIT {

    @ParameterizedTest
    @CsvSource({
            TEXT_EMBEDDING_ADA_002 + ",1536",
            TEXT_EMBEDDING_3_SMALL + ",1536",
            TEXT_EMBEDDING_3_LARGE + ",3072"
    })
    void should_embed_and_return_token_usage(String deploymentName, int expectedDimension) {

        EmbeddingModel model = AzureOpenAiEmbeddingModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new OpenAiTokenizer(deploymentName))
                .logRequestsAndResponses(true)
                .build();

        Response<Embedding> response = model.embed("hello world");

        assertThat(response.content().vector()).hasSize(expectedDimension);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);

        assertThat(response.finishReason()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            TEXT_EMBEDDING_ADA_002 + ",1536",
            TEXT_EMBEDDING_3_SMALL + ",1536",
            TEXT_EMBEDDING_3_LARGE + ",3072"
    })
    void should_embed_in_batches(String deploymentName, int expectedDimension) {

        int batchSize = 16;
        int numberOfSegments = batchSize + 1;

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < numberOfSegments; i++) {
            segments.add(TextSegment.from("text " + i));
        }

        EmbeddingModel model = AzureOpenAiEmbeddingModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(deploymentName)
                .tokenizer(new OpenAiTokenizer(deploymentName))
                .logRequestsAndResponses(true)
                .build();

        Response<List<Embedding>> response = model.embedAll(segments);

        assertThat(response.content()).hasSize(numberOfSegments);
        assertThat(response.content().get(0).dimension()).isEqualTo(expectedDimension);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(numberOfSegments * 3);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(numberOfSegments * 3);

        assertThat(response.finishReason()).isNull();
    }
}
