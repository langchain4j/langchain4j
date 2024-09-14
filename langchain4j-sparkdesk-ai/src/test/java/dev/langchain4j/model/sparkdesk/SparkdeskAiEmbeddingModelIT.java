package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SPARKDESK_API_KEY", matches = ".+")
public class SparkdeskAiEmbeddingModelIT {
    private static final String API_KEY = System.getenv("SPARKDESK_API_KEY");
    private static final String API_SECRET = System.getenv("SPARKDESK_API_SECRET");
    private static final String APP_ID = System.getenv("SPARKDESK_API_ID");

    SparkdeskAiEmbeddingModel model = SparkdeskAiEmbeddingModel.builder()
            .appId(APP_ID)
            .apiKey(API_KEY)
            .apiSecret(API_SECRET)
            .logRequests(true)
            .logResponses(true)
            .maxRetries(1)
            .build();

    @Test
    void should_embed_and_return_token_usage() {

        // given
        String text = "hello word";

        // when
        Response<Embedding> response = model.embed(text);
        System.out.println(response);

        assertThat(response.content().dimension()).isEqualTo(2560);
        // then

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_in_batches() {
        //I do not intend to support the trial version, so please ensure that your API is not a trial version. The trial version only has 2 QPS, and in this case, the test column will throw an exception with the exception code 11202.
        int batchSize = 10;
        int numberOfSegments = batchSize + 1;

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < numberOfSegments; i++) {
            segments.add(TextSegment.from("text " + i));
        }

        Response<List<Embedding>> response = model.embedAll(segments);
        System.out.println(response);

        assertThat(response.content()).hasSize(11);
        assertThat(response.content().get(0).dimension()).isEqualTo(2560);

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }
}
