package dev.langchain4j.model.voyageai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.model.voyageai.VoyageAiScoringModelName.RERANK_LITE_1;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

@EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
class VoyageAiScoringModelIT {

    @Test
    void should_score_single_text() {

        // given
        ScoringModel model = VoyageAiScoringModel.builder()
                .apiKey(System.getenv("VOYAGE_API_KEY"))
                .modelName(RERANK_LITE_1)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();

        String text = "labrador retriever";
        String query = "tell me about dogs";

        // when
        Response<Double> response = model.score(text, query);

        // then
        assertThat(response.content()).isCloseTo(0.35, withPercentage(3));

        assertThat(response.tokenUsage().inputTokenCount()).isPositive();
        assertThat(response.tokenUsage().outputTokenCount()).isNull();
        assertThat(response.tokenUsage().totalTokenCount()).isPositive();

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_score_multiple_segments_with_all_parameters() {

        // given
        ScoringModel model = VoyageAiScoringModel.builder()
                .apiKey(System.getenv("VOYAGE_API_KEY"))
                .modelName(RERANK_LITE_1)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();

        TextSegment catSegment = TextSegment.from("The Maine Coon is a large domesticated cat breed.");
        TextSegment dogSegment = TextSegment.from("The sweet-faced, lovable Labrador Retriever is one of America's most popular dog breeds, year after year.");
        List<TextSegment> segments = asList(catSegment, dogSegment);

        String query = "tell me about dogs";

        // when
        Response<List<Double>> response = model.scoreAll(segments, query);

        // then
        List<Double> scores = response.content();
        assertThat(scores).hasSize(2);
        assertThat(scores.get(0)).isLessThan(scores.get(1));

        assertThat(response.tokenUsage().inputTokenCount()).isNotNegative();
        assertThat(response.tokenUsage().outputTokenCount()).isNull();
        assertThat(response.tokenUsage().totalTokenCount()).isNotNegative();

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_respect_top_k() {
        // given
        ScoringModel model = VoyageAiScoringModel.builder()
                .apiKey(System.getenv("VOYAGE_API_KEY"))
                .modelName(RERANK_LITE_1)
                .timeout(Duration.ofSeconds(60))
                .topK(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        TextSegment catSegment = TextSegment.from("The Maine Coon is a large domesticated cat breed.");
        TextSegment dogSegment = TextSegment.from("The sweet-faced, lovable Labrador Retriever is one of America's most popular dog breeds, year after year.");
        List<TextSegment> segments = asList(catSegment, dogSegment);

        String query = "tell me about dogs";

        // when
        Response<List<Double>> response = model.scoreAll(segments, query);

        // then
        List<Double> scores = response.content();
        assertThat(scores).hasSize(1);

        assertThat(response.tokenUsage().inputTokenCount()).isNotNegative();
        assertThat(response.tokenUsage().outputTokenCount()).isNull();
        assertThat(response.tokenUsage().totalTokenCount()).isNotNegative();

        assertThat(response.finishReason()).isNull();
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VOYAGE_AI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
