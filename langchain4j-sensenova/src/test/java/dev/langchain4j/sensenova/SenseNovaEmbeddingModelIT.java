package dev.langchain4j.sensenova;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.sensenova.SenseNovaEmbeddingModel;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SENSENOVA_API_KEY_ID", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SenseNovaEmbeddingModelIT {
    private String apiKeyId = System.getenv("SENSENOVA_API_KEY_ID");
    private String apiKeySecret = System.getenv("SENSENOVA_API_KEY_SECRET");

    SenseNovaEmbeddingModel model = SenseNovaEmbeddingModel.builder()
            .apiKeyId(apiKeyId)
            .apiKeySecret(apiKeySecret)
            .logRequests(true)
            .logResponses(true)
            .maxRetries(1)
            .build();

    @Test
    @Order(1)
    void should_embed_and_return_token_usage() {

        sleep();

        // given
        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);
        System.out.println(response);

        assertThat(response.content().dimension()).isEqualTo(768);
        // then
        TokenUsage tokenUsage = response.tokenUsage();

        assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(0);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);
        assertThat(response.finishReason()).isNull();
    }

    @Test
    @Order(2)
    void should_embed_in_batches() {

        sleep();

        int batchSize = 10;
        int numberOfSegments = batchSize + 1;

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < numberOfSegments; i++) {
            segments.add(TextSegment.from("text " + i));
        }

        Response<List<Embedding>> response = model.embedAll(segments);
        System.out.println(response);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).dimension()).isEqualTo(768);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(22);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(0);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(22);

        assertThat(response.finishReason()).isNull();
    }


    private void sleep() {
        try {
            Thread.sleep(1000L);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
