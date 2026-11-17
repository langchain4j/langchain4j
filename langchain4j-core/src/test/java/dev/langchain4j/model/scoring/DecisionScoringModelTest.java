package dev.langchain4j.model.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelResponseContext;
import dev.langchain4j.model.scoring.request.DefaultScoringRequestParameters;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionScoringModelTest {

    // answers "yes" with a probability derived from the document text: "relevant" -> 0.9, otherwise 0.1
    final MockDecisionModel decisionModel = MockDecisionModel.thatAnswersYesNoQuestions(question -> question.text()
                    .replace("Does the document help answer the query?", "")
                    .contains("relevant")
            ? 0.9
            : 0.1)
            .thenRepeats();

    @Test
    void should_score_segments_with_the_probability_of_yes() {

        ScoringModel scoringModel = new DecisionScoringModel(decisionModel);

        Response<List<Double>> response = scoringModel.scoreAll(
                List.of(TextSegment.from("a relevant document"), TextSegment.from("something else")), "the query");

        assertThat(response.content()).containsExactly(0.9, 0.1);
        assertThat(decisionModel.request().input()).isEqualTo(FieldsInput.from(Map.of("query", "the query")));
        assertThat(decisionModel.request().questions())
                .containsEntry(
                        "document1",
                        YesNoQuestion.from("Does the document help answer the query?\nDocument: a relevant document"))
                .containsEntry(
                        "document2",
                        YesNoQuestion.from("Does the document help answer the query?\nDocument: something else"));
    }

    @Test
    void should_notify_listeners_once_per_scoring_call() {

        List<ScoringModelResponseContext> responses = new ArrayList<>();
        ScoringModelListener listener = new ScoringModelListener() {
            @Override
            public void onResponse(ScoringModelResponseContext responseContext) {
                responses.add(responseContext);
            }
        };
        ScoringModel scoringModel = DecisionScoringModel.builder()
                .decisionModel(reporting(decisionModel, new TokenUsage(3), null, ModelProvider.OPEN_AI))
                .maxSegmentsPerRequest(1)
                .listeners(listener)
                .build();

        scoringModel.scoreAll(List.of(TextSegment.from("a relevant document"), TextSegment.from("other")), "q");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).scoringRequest().documents()).containsExactly("a relevant document", "other");
        assertThat(responses.get(0).scoringResponse().scores()).containsExactly(0.9, 0.1);
        assertThat(responses.get(0).scoringResponse().tokenUsage()).isEqualTo(new TokenUsage(6));
        assertThat(responses.get(0).modelProvider()).isEqualTo(ModelProvider.OPEN_AI);
    }

    @Test
    void should_report_the_model_name_of_the_decision_model() {

        ScoringModel scoringModel = new DecisionScoringModel(reporting(decisionModel, null, "decision-model-v2", null));
        ScoringRequest request = ScoringRequest.builder()
                .documents(List.of("a relevant document"))
                .query("the query")
                .build();

        assertThat(scoringModel.score(request).modelName()).isEqualTo("decision-model-v2");
        assertThat(scoringModel.scoreAsync(request).join().modelName()).isEqualTo("decision-model-v2");
    }

    @Test
    void should_report_the_requested_model_name_when_the_decision_model_reports_none() {

        ScoringModel scoringModel = new DecisionScoringModel(decisionModel);
        ScoringRequest request = ScoringRequest.builder()
                .documents(List.of("a relevant document"))
                .query("the query")
                .parameters(DefaultScoringRequestParameters.builder().modelName("per-call-model").build())
                .build();

        assertThat(scoringModel.score(request).modelName()).isEqualTo("per-call-model");
        assertThat(scoringModel.scoreAsync(request).join().modelName()).isEqualTo("per-call-model");
    }

    @Test
    void should_fail_async_scoring_with_blank_query_without_throwing() {

        CompletableFuture<ScoringResponse> future = new DecisionScoringModel(decisionModel)
                .scoreAsync(ScoringRequest.builder()
                        .documents(List.of("relevant"))
                        .query(" ")
                        .build());

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(decisionModel.requests()).isEmpty();
    }

    @Test
    void score_should_pass_the_per_call_model_name_to_the_decision_model() {

        ScoringModel scoringModel = new DecisionScoringModel(decisionModel);

        ScoringResponse response = scoringModel.score(ScoringRequest.builder()
                .documents(List.of("a relevant document"))
                .query("the query")
                .parameters(DefaultScoringRequestParameters.builder().modelName("per-call-model").build())
                .build());

        assertThat(response.scores()).containsExactly(0.9);
        assertThat(decisionModel.request().parameters().modelName()).isEqualTo("per-call-model");
    }

    @Test
    void should_fail_when_decision_model_refuses_to_score_a_segment() {

        ScoringModel scoringModel =
                new DecisionScoringModel(MockDecisionModel.thatAnswersQuestions(question -> RefusalAnswer.of()));

        assertThatThrownBy(() -> scoringModel.scoreAll(List.of(TextSegment.from("a document")), "the query"))
                .isInstanceOf(ContentFilteredException.class);
    }

    @Test
    void should_score_in_batches_with_custom_question_template() {

        ScoringModel scoringModel = DecisionScoringModel.builder()
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
                .containsEntry("document1", YesNoQuestion.from("Is this document useful? relevant 2"));
    }

    @Test
    void should_score_up_to_20_segments_per_request_by_default() {

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            segments.add(TextSegment.from("relevant " + i));
        }

        Response<List<Double>> response = new DecisionScoringModel(decisionModel).scoreAll(segments, "query");

        assertThat(response.content()).hasSize(50).containsOnly(0.9);
        assertThat(decisionModel.requests())
                .extracting(request -> request.questions().size())
                .containsExactly(20, 20, 10);
    }

    @Test
    void should_sum_token_usage_of_batches() {

        ScoringModel scoringModel = DecisionScoringModel.builder()
                .decisionModel(reporting(decisionModel, new TokenUsage(10, 1), null, null))
                .maxSegmentsPerRequest(2)
                .build();

        Response<List<Double>> response = scoringModel.scoreAll(
                List.of(TextSegment.from("relevant"), TextSegment.from("other"), TextSegment.from("relevant")),
                "query");

        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(20, 2));
    }

    @Test
    void should_return_no_scores_for_no_segments() {

        Response<List<Double>> response = new DecisionScoringModel(decisionModel).scoreAll(List.of(), "query");

        assertThat(response.content()).isEmpty();
        assertThat(decisionModel.requests()).isEmpty();
    }

    @Test
    void should_score_asynchronously() {

        ScoringModel scoringModel = DecisionScoringModel.builder()
                .decisionModel(reporting(decisionModel, new TokenUsage(10, 1), null, null))
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
    void should_pass_model_name_of_async_request_to_decision_model() {

        new DecisionScoringModel(decisionModel)
                .scoreAsync(ScoringRequest.builder()
                        .documents(List.of("relevant"))
                        .query("query")
                        .parameters(DefaultScoringRequestParameters.builder()
                                .modelName("my-model")
                                .build())
                        .build())
                .join();

        assertThat(decisionModel.request().modelName()).isEqualTo("my-model");
    }

    @Test
    void should_fail_async_scoring_when_response_has_no_answer() {

        DecisionModel withoutAnswers =
                MockDecisionModel.thatAnswers(Map.of("unknown", YesNoAnswer.from(0.9)));

        assertThat(new DecisionScoringModel(withoutAnswers)
                        .scoreAsync(ScoringRequest.builder()
                                .documents(List.of("text"))
                                .query("query")
                                .build()))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(InvalidDecisionResponseException.class);
    }

    @Test
    void should_require_document_in_question_template() {

        assertThatThrownBy(() -> DecisionScoringModel.builder()
                        .decisionModel(decisionModel)
                        .questionTemplate(PromptTemplate.from("Is it relevant?"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{{document}}");
    }

    @Test
    void should_reject_question_template_with_unknown_variables() {

        assertThatThrownBy(() -> DecisionScoringModel.builder()
                        .decisionModel(decisionModel)
                        .questionTemplate(PromptTemplate.from("Does {{document}} answer {{question}}?"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can only use the {{document}} variable");
    }

    @Test
    void should_propagate_decision_model_errors() {

        ScoringModel scoringModel =
                new DecisionScoringModel(
                        MockDecisionModel.thatFails(new RuntimeException("down")).thenRepeats());

        assertThatThrownBy(() -> scoringModel.scoreAll(List.of(TextSegment.from("text")), "query"))
                .hasMessage("down");
        assertThat(scoringModel.scoreAsync(ScoringRequest.builder()
                        .documents(List.of("text"))
                        .query("query")
                        .build()))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingRootCause()
                .withMessage("down");
    }

    @Test
    void should_cancel_decision_model_call_when_async_scoring_is_cancelled() {

        CompletableFuture<DecisionResponse> inFlight = new CompletableFuture<>();
        MockDecisionModel pending = spy(MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.5)); // its answer never arrives
        doReturn(inFlight).when(pending).doDecideAsync(any());

        new DecisionScoringModel(pending)
                .scoreAsync(ScoringRequest.builder()
                        .documents(List.of("text"))
                        .query("query")
                        .build())
                .cancel(true);

        assertThat(inFlight).isCancelled();
    }

    @Test
    void should_fail_async_scoring_and_cancel_other_batches_when_a_batch_fails() {

        CompletableFuture<DecisionResponse> pending = new CompletableFuture<>();
        MockDecisionModel decisionModel =
                spy(MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.5)); // the first batch fails
        doAnswer(invocation -> ((DecisionRequest) invocation.getArgument(0))
                                .questions().values().stream()
                                        .anyMatch(question -> question.text().contains("first"))
                        ? CompletableFuture.failedFuture(new RuntimeException("down"))
                        : pending)
                .when(decisionModel)
                .doDecideAsync(any());

        CompletableFuture<ScoringResponse> response = DecisionScoringModel.builder()
                .decisionModel(decisionModel)
                .maxSegmentsPerRequest(1)
                .build()
                .scoreAsync(ScoringRequest.builder()
                        .documents(List.of("first", "second"))
                        .query("query")
                        .build());

        assertThat(response)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingRootCause()
                .withMessage("down");
        assertThat(pending).isCancelled();
    }

    @Test
    void should_validate_configuration() {

        assertThatThrownBy(() -> new DecisionScoringModel((DecisionModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisionModel");
        assertThatThrownBy(() -> DecisionScoringModel.builder()
                        .decisionModel(decisionModel)
                        .maxSegmentsPerRequest(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSegmentsPerRequest");
    }

    @SuppressWarnings("unchecked")
    private static MockDecisionModel reporting(
            MockDecisionModel model, TokenUsage tokenUsage, String modelName, ModelProvider provider) {
        MockDecisionModel reporting = spy(model); // a decision model that reports token usage, a model name and a provider
        doAnswer(invocation -> withMetadata((DecisionResponse) invocation.callRealMethod(), tokenUsage, modelName))
                .when(reporting)
                .doDecide(any());
        doAnswer(invocation -> ((CompletableFuture<DecisionResponse>) invocation.callRealMethod())
                        .thenApply(response -> withMetadata(response, tokenUsage, modelName)))
                .when(reporting)
                .doDecideAsync(any());
        if (provider != null) {
            doReturn(provider).when(reporting).provider();
        }
        return reporting;
    }

    private static DecisionResponse withMetadata(DecisionResponse response, TokenUsage tokenUsage, String modelName) {
        return DecisionResponse.builder()
                .answers(response.answers())
                .tokenUsage(tokenUsage)
                .modelName(modelName)
                .build();
    }
}
