package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
public class BedrockCohereEmbeddingModelIT {

    @Test
    void testCohereMultilingualEmbeddingModel() {
        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel
                .builder()
                .region(Region.US_EAST_1)
                .maxRetries(1)
                .model("cohere.embed-multilingual-v3")
                .inputType(BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY.getValue())
                .build();

        assertThat(embeddingModel).isNotNull();

        Response<Embedding> response = embeddingModel.embed(TextSegment.from("How are you?"));
        assertThat(response).isNotNull();

        Embedding embedding = response.content();

        assertThat(embedding.vector()).hasSize(1024);

        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @Test
    void testCohereMultilingualEmbeddingModelBatch() {
        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel
                .builder()
                .region(Region.US_EAST_1)
                .maxRetries(1)
                .model("cohere.embed-multilingual-v3")
                .inputType(BedrockCohereEmbeddingModel.InputType.SEARCH_QUERY.getValue())
                .build();

        assertThat(embeddingModel).isNotNull();

        List<TextSegment> segments = List.of(TextSegment.from("How are you?"), TextSegment.from("What is your name?"));

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(2);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(1024);

        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }
}
