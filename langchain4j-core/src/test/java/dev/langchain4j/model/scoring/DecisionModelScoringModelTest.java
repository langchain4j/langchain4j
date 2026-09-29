package dev.langchain4j.model.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionModelScoringModelTest {

    // answers "yes" with a probability derived from the document text: "relevant" -> 0.9, otherwise 0.1
    final DecisionModelMock decisionModel = DecisionModelMock.thatAnswersYesNoQuestions(question -> question.text()
                    .replace("Does the document help answer the query?", "")
                    .contains("relevant")
            ? 0.9
            : 0.1);

    @Test
    void should_score_segments_with_the_probability_of_yes() {

        ScoringModel scoringModel = new DecisionModelScoringModel(decisionModel);

        Response<List<Double>> response = scoringModel.scoreAll(
                List.of(TextSegment.from("a relevant document"), TextSegment.from("something else")), "the query");

        assertThat(response.content()).containsExactly(0.9, 0.1);
        assertThat(decisionModel.request().input()).isEqualTo(Map.of("query", "the query"));
        assertThat(decisionModel.request().questions())
                .containsEntry(
                        "document1",
                        YesNoQuestion.of("Does the document help answer the query?\nDocument: a relevant document"))
                .containsEntry(
                        "document2",
                        YesNoQuestion.of("Does the document help answer the query?\nDocument: something else"));
    }

    @Test
    void should_score_in_batches_and_sum_token_usage() {

        ScoringModel scoringModel = DecisionModelScoringModel.builder()
                .decisionModel(decisionModel)
                .maxSegmentsPerRequest(2)
                .questionTemplate(PromptTemplate.from("Is this document useful? {{document}}"))
                .build();

        Response<List<Double>> response = scoringModel.scoreAll(
                List.of(
                        TextSegment.from("relevant 1"),
                        TextSegment.from("other"),
                        TextSegment.from("relevant 2")),
                "query");

        assertThat(response.content()).containsExactly(0.9, 0.1, 0.9);
        assertThat(decisionModel.requests()).hasSize(2);
        assertThat(decisionModel.requests().get(1).questions())
                .containsOnlyKeys("document1")
                .containsEntry("document1", YesNoQuestion.of("Is this document useful? relevant 2"));
    }

    @Test
    void should_score_all_segments_in_a_single_request_by_default() {

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            segments.add(TextSegment.from("relevant " + i));
        }

        Response<List<Double>> response = new DecisionModelScoringModel(decisionModel).scoreAll(segments, "query");

        assertThat(response.content()).hasSize(50).containsOnly(0.9);
        assertThat(decisionModel.requests()).hasSize(1);
    }

    @Test
    void should_sum_token_usage_of_batches() {

        decisionModel.withTokenUsage(new TokenUsage(10, 1));
        ScoringModel scoringModel = DecisionModelScoringModel.builder()
                .decisionModel(decisionModel)
                .maxSegmentsPerRequest(2)
                .build();

        Response<List<Double>> response = scoringModel.scoreAll(
                List.of(TextSegment.from("relevant"), TextSegment.from("other"), TextSegment.from("relevant")),
                "query");

        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(20, 2));
    }

    @Test
    void should_return_no_scores_for_no_segments() {

        Response<List<Double>> response = new DecisionModelScoringModel(decisionModel).scoreAll(List.of(), "query");

        assertThat(response.content()).isEmpty();
        assertThat(decisionModel.requests()).isEmpty();
    }

    @Test
    void should_score_asynchronously() {

        decisionModel.withTokenUsage(new TokenUsage(10, 1));
        ScoringModel scoringModel = DecisionModelScoringModel.builder()
                .decisionModel(decisionModel)
                .maxSegmentsPerRequest(2)
                .build();

        ScoringResponse response = scoringModel
                .scoreAsync(ScoringRequest.builder()
                        .documents(List.of("relevant", "other", "relevant"))
                        .query("query")
                        .build())
                .join();

        assertThat(response.scores()).containsExactly(0.9, 0.1, 0.9);
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(20, 2));
    }

    @Test
    void should_require_document_in_question_template() {

        assertThatThrownBy(() -> DecisionModelScoringModel.builder()
                        .decisionModel(decisionModel)
                        .questionTemplate(PromptTemplate.from("Is it relevant?"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{document}}");
    }

    @Test
    void should_validate_configuration() {

        assertThatThrownBy(() -> new DecisionModelScoringModel((DecisionModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisionModel");
        assertThatThrownBy(() -> DecisionModelScoringModel.builder()
                        .decisionModel(decisionModel)
                        .maxSegmentsPerRequest(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSegmentsPerRequest");
    }
}
