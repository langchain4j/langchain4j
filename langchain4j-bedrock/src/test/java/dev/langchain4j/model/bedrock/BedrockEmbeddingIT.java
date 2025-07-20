package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockEmbeddingIT {

    @Test
    void bedrockTitanEmbeddingModelV1() {

        BedrockTitanEmbeddingModel embeddingModel = BedrockTitanEmbeddingModel.builder()
                .region(Region.US_EAST_1)
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
    void bedrockTitanEmbeddingModelV2() {
        BedrockTitanEmbeddingModel embeddingModel = BedrockTitanEmbeddingModel.builder()
                .region(Region.US_EAST_1)
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
    void injectClientToModelBuilder() {

        String serviceName = "custom-service-name";

        BedrockTitanEmbeddingModel model = BedrockTitanEmbeddingModel.builder()
                .client(new BedrockRuntimeClient() {
                    @Override
                    public String serviceName() {
                        return serviceName;
                    }

                    @Override
                    public void close() {}
                })
                .model(BedrockTitanEmbeddingModel.Types.TitanEmbedTextV2.getValue())
                .build();

        assertThat(model.getClient().serviceName()).isEqualTo(serviceName);
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
