package dev.langchain4j.model.scoring;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.request.ScoringRequestParameters;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void scoreAsync_applies_default_parameters_and_dispatches_to_doScoreAsync() throws Exception {

        // given: a model whose doScoreAsync echoes back the (defaults-applied) request
        ScoringModel model = new ScoringModel() {
            @Override
            public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
                return Response.from(singletonList(SCORE));
            }

            @Override
            public CompletableFuture<ScoringResponse> doScoreAsync(ScoringRequest request) {
                return CompletableFuture.completedFuture(ScoringResponse.builder()
                        .scores(List.of(SCORE, SCORE))
                        .modelName(request.modelName())
                        .build());
            }

            @Override
            public ScoringRequestParameters defaultRequestParameters() {
                return ScoringRequestParameters.builder().modelName("default-model").build();
            }
        };

        // when: the request carries no model name, so the default applies
        ScoringResponse response = model.scoreAsync(ScoringRequest.builder()
                        .documents(List.of("a", "b"))
                        .query("q")
                        .build())
                .get();

        // then
        assertThat(response.scores()).containsExactly(SCORE, SCORE);
        assertThat(response.modelName()).isEqualTo("default-model");
    }

    @Test
    void scoreAsync_default_fails_loudly_when_not_implemented() {

        // given: a model that only implements the blocking scoreAll
        ScoringModel model = new TestScoringModel();

        // when
        CompletableFuture<ScoringResponse> future = model.scoreAsync(
                ScoringRequest.builder().documents(List.of("a")).query("q").build());

        // then
        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get).hasCauseInstanceOf(AsyncNotSupportedException.class);
        assertThat(model.defaultRequestParameters()).isEqualTo(ScoringRequestParameters.EMPTY);
    }
}
