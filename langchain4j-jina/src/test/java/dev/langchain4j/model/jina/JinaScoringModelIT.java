package dev.langchain4j.model.jina;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "JINA_API_KEY", matches = ".+")
class JinaScoringModelIT {

    @Test
    @DisplayName("Single text to score, using Jina scoring model: jina-reranker-v2-base-multilingual")
    void should_score_single_text() {

        // given
        ScoringModel model = JinaScoringModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-reranker-v2-base-multilingual")
                .build();

        String text = "labrador retriever";
        String query = "tell me about dogs";

        // when
        Response<Double> response = model.score(text, query);

        // then
        assertThat(response.content()).isCloseTo(0.1, withPercentage(10));

        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(14);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    @DisplayName("Multiple text segments to score, using Jina scoring model: jina-reranker-v2-base-multilingual")
    void should_score_multiple_segments_with_all_parameters() {

        // given
        ScoringModel model = JinaScoringModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-reranker-v2-base-multilingual")
                .timeout(Duration.ofSeconds(10))
                .logResponses(true)
                .build();

        TextSegment catSegment = TextSegment.from("main coon");
        TextSegment dogSegment = TextSegment.from("labrador retriever");
        List<TextSegment> segments = asList(catSegment, dogSegment);

        String query = "tell me about dogs";

        // when
        Response<List<Double>> response = model.scoreAll(segments, query);

        // then
        List<Double> scores = response.content();
        assertThat(scores).hasSize(2);
        assertThat(scores.get(0)).isLessThan(scores.get(1));

        assertThat(response.tokenUsage().totalTokenCount()).isPositive();

        assertThat(response.finishReason()).isNull();
    }

    @Test
    @DisplayName(
            "Multiple text segments to score, using Jina scoring model: jina-reranker-v1-turbo-en. Note: latency could be quite high for v1, adjusted the timeout")
    void should_score_multiple_segments_with_all_parameters_v1_reranker() {

        // given
        ScoringModel model = JinaScoringModel.builder()
                .apiKey(System.getenv("JINA_API_KEY"))
                .modelName("jina-reranker-v1-turbo-en")
                .timeout(Duration.ofSeconds(120))
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

        assertThat(response.tokenUsage().totalTokenCount()).isPositive();

        assertThat(response.finishReason()).isNull();
    }
}
