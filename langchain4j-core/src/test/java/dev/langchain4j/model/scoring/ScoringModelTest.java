package dev.langchain4j.model.scoring;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringModelTest {

    private static final double SCORE = 0.7;

    static class TestScoringModel implements ScoringModel {

        @Override
        public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
            return Response.from(singletonList(SCORE));
        }
    }

    @Test
    void should_score_text() {

        // given
        String text = "text";

        ScoringModel model = new TestScoringModel();

        // when
        Response<Double> response = model.score(text, "query");

        // then
        assertThat(response.content()).isEqualTo(SCORE);
    }

    @Test
    void should_score_text_segment() {

        // given
        TextSegment segment = TextSegment.from("text");

        ScoringModel model = new TestScoringModel();

        // when
        Response<Double> response = model.score(segment, "query");

        // then
        assertThat(response.content()).isEqualTo(SCORE);
    }
}