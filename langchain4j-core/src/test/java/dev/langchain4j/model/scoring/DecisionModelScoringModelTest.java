package dev.langchain4j.model.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionModelScoringModelTest {

    // answers "yes" with a probability derived from the document text: "relevant" -> 0.9, otherwise 0.1
    final DecisionModelMock decisionModel = DecisionModelMock.thatAnswers(request -> {
        @SuppressWarnings("unchecked")
        Map<String, String> documents = (Map<String, String>) ((Map<String, Object>) request.input()).get("documents");
        Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
        documents.forEach((index, text) ->
                answers.put("document" + index, YesNoAnswer.of(text.contains("relevant") ? 0.9 : 0.1)));
        return answers;
    });

    @Test
    void should_score_segments_with_the_probability_of_yes() {

        ScoringModel scoringModel = new DecisionModelScoringModel(decisionModel);

        Response<List<Double>> response = scoringModel.scoreAll(
                List.of(TextSegment.from("a relevant document"), TextSegment.from("something else")), "the query");

        assertThat(response.content()).containsExactly(0.9, 0.1);
        assertThat(decisionModel.requests()).hasSize(1);
        assertThat(decisionModel.requests().get(0).input())
                .isEqualTo(Map.of(
                        "query", "the query",
                        "documents", Map.of("1", "a relevant document", "2", "something else")));
        assertThat(decisionModel.requests().get(0).questions())
                .containsEntry("document1", YesNoQuestion.of("Document 1: Does the document help answer the query?"))
                .containsEntry("document2", YesNoQuestion.of("Document 2: Does the document help answer the query?"));
    }

    @Test
    void should_score_in_batches_and_sum_token_usage() {

        ScoringModel scoringModel = DecisionModelScoringModel.builder()
                .decisionModel(decisionModel)
                .maxSegmentsPerRequest(2)
                .question("Is the document relevant?")
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
                .containsEntry("document1", YesNoQuestion.of("Document 1: Is the document relevant?"));
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
