package dev.langchain4j.model.qianfan;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class QianfanEmbeddingModelIT {

    //see your api key and secret key here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private String apiKey = System.getenv("QIANFAN_API_KEY");
    private String secretKey = System.getenv("QIANFAN_SECRET_KEY");

    QianfanEmbeddingModel model = QianfanEmbeddingModel.builder().user("111").apiKey(apiKey)
            .secretKey(secretKey).endpoint("embedding-v1").build();



    @Test
    void should_embed_and_return_token_usage() {

        // given
        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);
        System.out.println(response);

        assertThat(response.content().vector()).hasSize(384);
        // then
        TokenUsage tokenUsage = response.tokenUsage();

        assertThat(tokenUsage.inputTokenCount()).isEqualTo(2);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(2);
        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_in_batches() {

        int batchSize = 10;
        int numberOfSegments = batchSize + 1;

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < numberOfSegments; i++) {
            segments.add(TextSegment.from("text " + i));
        }

        Response<List<Embedding>> response = model.embedAll(segments);
        System.out.println(response);

        assertThat(response.content()).hasSize(numberOfSegments);
        assertThat(response.content().get(0).dimension()).isEqualTo(384);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(numberOfSegments * 3+1);
        assertThat(tokenUsage.outputTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(numberOfSegments * 3+1);

        assertThat(response.finishReason()).isNull();
    }
}