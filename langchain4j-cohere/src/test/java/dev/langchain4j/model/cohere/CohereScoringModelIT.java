package dev.langchain4j.model.cohere;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

class CohereScoringModelIT {

    @Test
    void should_score_single_text() {

        // given
        ScoringModel model = CohereScoringModel.withApiKey(System.getenv("COHERE_API_KEY"));

        String text = "labrador retriever";
        String query = "tell me about dogs";

        // when
        Response<Double> response = model.score(text, query);

        // then
        assertThat(response.content()).isCloseTo(0.034, withPercentage(1));

        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(1);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_score_multiple_segments_with_all_parameters() {

        // given
        ScoringModel model = CohereScoringModel.builder()
                .baseUrl("https://api.cohere.ai/v1/")
                .apiKey(System.getenv("COHERE_API_KEY"))
                .timeout(Duration.ofSeconds(30))
                .modelName("rerank-multilingual-v2.0")
                .logRequests(true)
                .logResponses(true)
                .build();

        TextSegment catSegment = TextSegment.from("maine coon");
        TextSegment dogSegment = TextSegment.from("labrador retriever");
        List<TextSegment> segments = asList(catSegment, dogSegment);

        String query = "tell me about dogs";

        // when
        Response<List<Double>> response = model.scoreAll(segments, query);

        // then
        List<Double> scores = response.content();
        assertThat(scores).hasSize(2);
        assertThat(scores.get(0)).isLessThan(scores.get(1));

        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(1);

        assertThat(response.finishReason()).isNull();
    }
}