package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockEmbeddingIT {

    @Test
    void testBedrockTitanEmbeddingModelV1() {

        BedrockTitanEmbeddingModel embeddingModel = BedrockTitanEmbeddingModel
            .builder()
            .region(Region.US_EAST_1)
            .maxRetries(1)
            .model(BedrockTitanEmbeddingModel.Types.TitanEmbedTextV1.getValue())
            .build();

        assertThat(embeddingModel).isNotNull();

        List<TextSegment> segments = Collections.singletonList(TextSegment.from("one"));

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(1);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(1536);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(1);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(1);

        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1536);
    }

    @Test
    void testBedrockTitanEmbeddingModelV2() {
        BedrockTitanEmbeddingModel embeddingModel = BedrockTitanEmbeddingModel
            .builder()
            .region(Region.US_EAST_1)
            .maxRetries(1)
            .model(BedrockTitanEmbeddingModel.Types.TitanEmbedTextV2.getValue())
            .dimensions(256)
            .normalize(true)
            .build();

        assertThat(embeddingModel).isNotNull();

        List<TextSegment> segments = Collections.singletonList(TextSegment.from("How are you?"));

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(1);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(256);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(5);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(5);

        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(256);
    }

    @Test
    void testBedrockTitanEmbeddingModelV2MultipleSegments() {

        // given
        BedrockTitanEmbeddingModel embeddingModel = BedrockTitanEmbeddingModel.builder()
            .model(BedrockTitanEmbeddingModel.Types.TitanEmbedTextV2.getValue())
            .build();

        List<TextSegment> segments = List.of(
            TextSegment.from("Hello world!"),
            TextSegment.from("How are you?")
        );

        // when
        Response<List<Embedding>> response = embeddingModel.embedAll(segments);

        // then
        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(2);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(1024);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(9);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(9);

        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @Test
    void testBedrockCohereEmbedEnglishTextV3() {

        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel
            .builder()
            .region(Region.US_EAST_1)
            .maxRetries(1)
            .model(BedrockCohereEmbeddingModel.Types.CohereEmbedEnglishTextV3.getValue())
            .build();

        assertThat(embeddingModel).isNotNull();

        List<TextSegment> segments = Collections.singletonList(TextSegment.from("one"));

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(1);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(1024);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(0);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(0);

        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

    @Test
    void testBedrockCohereEmbedEnglishTextV3MultipleTextSegments() {

        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel
            .builder()
            .region(Region.US_EAST_1)
            .maxRetries(1)
            .model(BedrockCohereEmbeddingModel.Types.CohereEmbedEnglishTextV3.getValue())
            .build();

        assertThat(embeddingModel).isNotNull();

        List<TextSegment> segments = new ArrayList<>();
        segments.add(TextSegment.from("Hello world!"));
        segments.add(TextSegment.from("How are you?"));

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(2);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(1024);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(0);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(0);

        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }


    @Test
    void testBedrockCohereEmbedMultilingualTextV3() {

        BedrockCohereEmbeddingModel embeddingModel = BedrockCohereEmbeddingModel
            .builder()
            .region(Region.US_EAST_1)
            .maxRetries(1)
            .model(BedrockCohereEmbeddingModel.Types.CohereEmbedMultilingualTextV3.getValue())
            .build();

        assertThat(embeddingModel).isNotNull();

        List<TextSegment> segments = Collections.singletonList(TextSegment.from("one"));

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        assertThat(response).isNotNull();

        List<Embedding> embeddings = response.content();
        assertThat(embeddings).hasSize(1);

        Embedding embedding = embeddings.get(0);
        assertThat(embedding.vector()).hasSize(1024);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(0);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(0);

        assertThat(response.finishReason()).isNull();

        assertThat(embeddingModel.dimension()).isEqualTo(1024);
    }

}
