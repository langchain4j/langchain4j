package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BedrockEmbeddingIT {

    @Test
    @Disabled("To run this test, you must have provide your own access key, secret, region")
    void testBedrockTitanChatModel() {

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
    }

}
