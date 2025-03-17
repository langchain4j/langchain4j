package dev.langchain4j.model.jina;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

class JinaScoringModelIT {

    @Test
    void should_score_single_text() {

        // given
        ScoringModel model = JinaScoringModel.withApiKey(System.getenv("JINA_API_KEY"));

        String text = "labrador retriever";
        String query = "tell me about dogs";

        // when
        Response<Double> response = model.score(text, query);

        // then
        assertThat(response.content()).isCloseTo(0.25, withPercentage(1));

        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(12);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_score_multiple_segments_with_all_parameters() {

        // given
        ScoringModel model = JinaScoringModel.builder()
                .baseUrl("https://api.jina.ai/v1/")
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-reranker-v1-turbo-en")
                .timeout(Duration.ofSeconds(10))
                .maxRetries(2)
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

        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);

        assertThat(response.finishReason()).isNull();
    }
}