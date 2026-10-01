package dev.langchain4j.service.decision;

import dev.langchain4j.service.decision.internal.DecisionMethod;
import dev.langchain4j.service.decision.internal.DecisionServiceConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.InvalidDecisionResponseException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class DecisionServicesTest {

    enum Team {
        @Description("Payments, invoices, refunds")
        BILLING,
        @Description("Problems using the product")
        SUPPORT,
        SALES
    }

    static class FakeDecisionModel implements DecisionModel {

        final List<DecisionRequest> requests = new ArrayList<>();
        final Map<String, DecisionAnswer> answers;

        FakeDecisionModel(Map<String, DecisionAnswer> answers) {
            this.answers = answers;
        }

        @Override
        public DecisionResponse doDecide(DecisionRequest request) {
            requests.add(request);
            return DecisionResponse.builder()
                    .answers(answers)
                    .modelName("fake-model")
                    .tokenUsage(new TokenUsage(10, 2))
                    .build();
        }

        @Override
        public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
            return CompletableFuture.completedFuture(doDecide(request));
        }

        @Override
        public DecisionRequestParameters defaultRequestParameters() {
            return DecisionRequestParameters.builder().modelName("fake-default-model").build();
        }

        DecisionRequest request() {
            assertThat(requests).hasSize(1);
            return requests.get(0);
        }
    }

    private static YesNoAnswer yesNo(double probability) {
        return YesNoAnswer.builder().probability(probability).build();
    }

    private static final ChoiceAnswer BILLING_ANSWER = ChoiceAnswer.builder()
            .value("BILLING")
            .probability("BILLING", 0.8)
            .probability("SUPPORT", 0.15)
            .probability("SALES", 0.05)
            .confidence(0.7)
            .build();

    // yes/no questions

    interface SpamFilter {

        @Decide("Is this message spam?")
        boolean isSpam(@V("message") String message);
    }

    @Test
    void should_ask_yes_no_question_and_return_boolean() {

        // given
        FakeDecisionModel model = new FakeDecisionModel(Map.of("isSpam", yesNo(0.7)));
        SpamFilter spamFilter =
                DecisionServices.builder(SpamFilter.class).decisionModel(model).build();

        // when
        boolean spam = spamFilter.isSpam("You won a cruise!");

        // then
        assertThat(spam).isTrue();
        assertThat(model.request().input()).isEqualTo(Map.of("message", "You won a cruise!"));
        assertThat(model.request().questions())
                .isEqualTo(Map.of(
                        "isSpam",
                        YesNoQuestion.builder()
                                .text("Is this message spam?")
                                .build()));
    }

    @Test
    void should_use_default_threshold_of_0_5() {

        SpamFilter spamFilter = DecisionServices.builder(SpamFilter.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", yesNo(0.49))))
                .build();

        assertThat(spamFilter.isSpam("Hello")).isFalse();
    }

    @Test
    void should_use_threshold_provider() {

        // given
        List<ThresholdContext> requestedThresholds = new ArrayList<>();
        Map<String, Double> config = new java.util.HashMap<>(Map.of("isSpam", 0.9));
        SpamFilter spamFilter = DecisionServices.builder(SpamFilter.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", yesNo(0.7))))
                .thresholdProvider(context -> {
                    requestedThresholds.add(context);
                    return config.get(context.questionName());
                })
                .build();

        // when-then
        assertThat(spamFilter.isSpam("You won a cruise!")).isFalse();

        config.put("isSpam", 0.6); // e.g. configuration changed at runtime
        assertThat(spamFilter.isSpam("You won a cruise!")).isTrue();

        config.remove("isSpam"); // falls back to 0.5
        assertThat(spamFilter.isSpam("You won a cruise!")).isTrue();

        assertThat(requestedThresholds).hasSize(3);
        ThresholdContext context = requestedThresholds.get(0);
        assertThat(context.serviceInterface()).isEqualTo(SpamFilter.class);
        assertThat(context.method().getName()).isEqualTo("isSpam");
        assertThat(context.questionName()).isEqualTo("isSpam");
        assertThat(context.modelName()).isEqualTo("fake-model"); // the model that answered
    }

    @Test
    void should_reject_invalid_threshold() {

        SpamFilter spamFilter = DecisionServices.builder(SpamFilter.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", yesNo(0.7))))
                .thresholdProvider(context -> 1.5)
                .build();

        assertThatThrownBy(() -> spamFilter.isSpam("Hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The threshold for question 'isSpam' of method 'isSpam' must be between 0 and 1, but was 1.5");
    }

    @Test
    void async_methods_should_report_invalid_threshold_through_the_future() {

        AsyncSpamFilter spamFilter = DecisionServices.builder(AsyncSpamFilter.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", yesNo(0.7))))
                .thresholdProvider(context -> 1.5)
                .build();

        assertThat(spamFilter.isSpam("Hello", null))
                .failsWithin(java.time.Duration.ofSeconds(5))
                .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class)
                .withMessageContaining("question 'isSpam'");
    }

    @Test
    void async_methods_should_report_failures_of_the_model_call_through_the_future() {

        DecisionModel failingModel = new FakeDecisionModel(Map.of()) {
            @Override
            public CompletableFuture<DecisionResponse> decideAsync(DecisionRequest request) {
                throw new IllegalStateException("model misconfigured");
            }
        };
        AsyncSpamFilter spamFilter = DecisionServices.builder(AsyncSpamFilter.class)
                .decisionModel(failingModel)
                .build();

        assertThat(spamFilter.isSpam("Hello", null))
                .failsWithin(java.time.Duration.ZERO)
                .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                .withCauseInstanceOf(IllegalStateException.class);
    }

    interface SpamScorer {

        @Decide("Is this message spam?")
        YesNoAnswer isSpam(@V("message") String message);
    }

    @Test
    void should_return_yes_no() {

        SpamScorer spamScorer = DecisionServices.builder(SpamScorer.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", yesNo(0.7))))
                .build();

        YesNoAnswer spam = spamScorer.isSpam("You won a cruise!");

        assertThat(spam.probability()).isEqualTo(0.7);
        assertThat(spam.isYes(0.6)).isTrue();
        assertThat(spam.isYes(0.8)).isFalse();
    }

    interface SpamFilterWithParameters {

        @Decide("Is this message spam?")
        boolean isSpam(@V("message") String message, DecisionRequestParameters parameters);
    }

    @Test
    void should_pass_request_parameters_and_not_send_them_as_input() {

        // given
        FakeDecisionModel model = new FakeDecisionModel(Map.of("isSpam", yesNo(0.7)));
        SpamFilterWithParameters spamFilter = DecisionServices.builder(SpamFilterWithParameters.class)
                .decisionModel(model)
                .build();

        // when
        spamFilter.isSpam(
                "You won a cruise!",
                DecisionRequestParameters.builder().modelName("jev-1.13.0").build());

        // then
        assertThat(model.request().modelName()).isEqualTo("jev-1.13.0");
        assertThat(model.request().input()).isEqualTo(Map.of("message", "You won a cruise!"));
    }

    @Test
    void should_treat_null_request_parameters_as_no_override() {

        FakeDecisionModel model = new FakeDecisionModel(Map.of("isSpam", yesNo(0.7)));
        SpamFilterWithParameters spamFilter = DecisionServices.builder(SpamFilterWithParameters.class)
                .decisionModel(model)
                .build();

        assertThat(spamFilter.isSpam("Hello", null)).isTrue();
        assertThat(model.request().modelName()).isEqualTo("fake-default-model");
    }

    // choice questions

    interface Router {

        @Decide("Which team should handle this ticket?")
        Team route(@V("ticket") String ticket);

        @Decide("Which team should handle this ticket?")
        Choice<Team> routeWithProbabilities(@V("ticket") String ticket);
    }

    @Test
    void should_ask_choice_question_and_return_enum() {

        // given
        FakeDecisionModel model = new FakeDecisionModel(Map.of("route", BILLING_ANSWER));
        Router router = DecisionServices.builder(Router.class).decisionModel(model).build();

        // when
        Team team = router.route("I was charged twice");

        // then
        assertThat(team).isEqualTo(Team.BILLING);
        assertThat(model.request().questions())
                .isEqualTo(Map.of(
                        "route",
                        ChoiceQuestion.builder()
                                .text("Which team should handle this ticket?")
                                .option("BILLING", "Payments, invoices, refunds")
                                .option("SUPPORT", "Problems using the product")
                                .option("SALES")
                                .build()));
    }

    @Test
    void should_create_service_with_model_only() {

        SpamFilter spamFilter =
                DecisionServices.create(SpamFilter.class, new FakeDecisionModel(Map.of("isSpam", yesNo(0.7))));

        assertThat(spamFilter.isSpam("You won a cruise!")).isTrue();
    }

    @Test
    void should_return_choice_with_probabilities() {

        Router router = DecisionServices.builder(Router.class)
                .decisionModel(new FakeDecisionModel(Map.of("routeWithProbabilities", BILLING_ANSWER)))
                .build();

        Choice<Team> choice = router.routeWithProbabilities("I was charged twice");

        assertThat(choice.value()).isEqualTo(Team.BILLING);
        assertThat(choice.probabilities())
                .isEqualTo(new EnumMap<>(Map.of(Team.BILLING, 0.8, Team.SUPPORT, 0.15, Team.SALES, 0.05)));
        assertThat(choice.confidence()).isEqualTo(0.7);
    }

    @Test
    void choice_should_expose_probability_and_margin() {

        Choice<Team> choice = Choice.<Team>builder()
                .value(Team.BILLING)
                .probabilities(Map.of(Team.BILLING, 0.55, Team.SUPPORT, 0.4))
                .build();

        assertThat(choice.probabilityOf(Team.BILLING)).isEqualTo(0.55);
        assertThat(choice.probabilityOf(Team.SALES)).isZero();
        assertThat(choice.margin()).isCloseTo(0.15, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void choice_should_support_options_that_are_not_enum_constants() {

        Choice<String> choice = Choice.<String>builder()
                .value("refunds-agent")
                .probabilities(Map.of("refunds-agent", 0.7, "billing-agent", 0.3))
                .confidence(0.4)
                .build();

        assertThat(choice.value()).isEqualTo("refunds-agent");
        assertThat(choice.probabilityOf("billing-agent")).isEqualTo(0.3);
        assertThat(choice.margin()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void choice_should_fail_when_model_did_not_report_probabilities() {

        Choice<Team> choice = Choice.<Team>builder().value(Team.BILLING).build();

        assertThatThrownBy(() -> choice.probabilityOf(Team.BILLING)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(choice::margin).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_fail_when_model_chooses_unknown_option() {

        Router router = DecisionServices.builder(Router.class)
                .decisionModel(new FakeDecisionModel(Map.of(
                        "route", ChoiceAnswer.builder().value("MARKETING").build())))
                .build();

        assertThatThrownBy(() -> router.route("Hello"))
                .isInstanceOf(InvalidDecisionResponseException.class)
                .hasMessageContaining("MARKETING");
    }

    // scale questions

    enum Severity {
        @Description("Cosmetic issue, no impact")
        LOW,
        @Description("A feature is degraded, a workaround exists")
        MEDIUM,
        HIGH
    }

    interface IncidentTriage {

        @Decide("How severe is this incident?")
        Scale<Severity> severity(@V("incident") String incident);
    }

    @Test
    void should_ask_scale_question_with_enum_constants_as_levels() {

        FakeDecisionModel model = new FakeDecisionModel(Map.of(
                "severity",
                ScaleAnswer.builder()
                        .mean(1.3)
                        .probabilities(List.of(0.1, 0.5, 0.4))
                        .confidence(0.6)
                        .build()));
        IncidentTriage triage = DecisionServices.builder(IncidentTriage.class)
                .decisionModel(model)
                .build();

        Scale<Severity> severity = triage.severity("Checkout is slow for some customers");

        assertThat(model.request().questions())
                .containsExactly(Map.entry(
                        "severity",
                        ScaleQuestion.of(
                                "How severe is this incident?",
                                List.of(
                                        "LOW: Cosmetic issue, no impact",
                                        "MEDIUM: A feature is degraded, a workaround exists",
                                        "HIGH"))));
        assertThat(severity.mean()).isEqualTo(1.3);
        assertThat(severity.mostLikely()).isEqualTo(Severity.MEDIUM);
        assertThat(severity.probabilities()).containsExactly(
                Map.entry(Severity.LOW, 0.1), Map.entry(Severity.MEDIUM, 0.5), Map.entry(Severity.HIGH, 0.4));
        assertThat(severity.probabilityAtLeast(Severity.MEDIUM)).isCloseTo(0.9, offset(1e-9));
        assertThat(severity.confidence()).isEqualTo(0.6);
    }

    @Test
    void should_reject_scale_answer_with_wrong_number_of_levels() {

        IncidentTriage triage = DecisionServices.builder(IncidentTriage.class)
                .decisionModel(new FakeDecisionModel(Map.of(
                        "severity",
                        ScaleAnswer.builder().mean(0.5).probabilities(List.of(0.5, 0.5)).build())))
                .build();

        assertThatThrownBy(() -> triage.severity("Checkout is slow"))
                .isInstanceOf(InvalidDecisionResponseException.class)
                .hasMessageContaining("'severity' has 2 level probabilities, but the question has 3 levels");
    }

    @Test
    void scale_should_fall_back_to_the_closest_level_without_probabilities() {

        Scale<Severity> severity = Scale.builder(Severity.class).mean(1.6).build();

        assertThat(severity.mostLikely()).isEqualTo(Severity.HIGH);
        assertThat(severity.probabilities()).isEmpty();
        assertThatThrownBy(() -> severity.probabilityAtLeast(Severity.HIGH))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> severity.probabilityOf(Severity.HIGH)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void scale_should_validate_mean_and_probabilities() {

        assertThatThrownBy(() -> Scale.builder(Severity.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mean");
        assertThat(Scale.builder(Severity.class).mean(0.0).build().mean()).isZero();
        assertThat(Scale.builder(Severity.class).mean(2.0).build().mean()).isEqualTo(2.0);
        assertThatThrownBy(() -> Scale.builder(Severity.class).mean(2.5).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mean must be between 0 and 2");
        assertThatThrownBy(() -> Scale.builder(Severity.class)
                        .mean(1.0)
                        .probabilities(Map.of(Severity.LOW, 1.5))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");
        assertThat(Scale.builder(Severity.class)
                        .mean(1.0)
                        .probabilities(Map.of(Severity.HIGH, 0.2, Severity.LOW, 0.3))
                        .build()
                        .probabilityOf(Severity.MEDIUM))
                .isZero();
        assertThat(Scale.builder(Severity.class).mean(1.0).build())
                .isEqualTo(Scale.builder(Severity.class).mean(1.0).build())
                .isNotEqualTo(Scale.builder(Severity.class).mean(1.5).build());
    }

    // objects with several questions

    static class Triage {

        @Decide("Which team should handle this ticket?")
        Team team;

        @Decide("Does this need attention today?")
        boolean urgent;

        @Decide("Does the customer ask for money back?")
        YesNoAnswer refund;
    }

    record TriageRecord(
            @Decide("Which team should handle this ticket?") Choice<Team> team,
            @Decide("Does this need attention today?") boolean urgent,
            @Decide("Does the customer ask for money back?") boolean refund) {}

    interface SupportDesk {

        Triage triage(@V("ticket") String ticket, @V("plan") String plan);

        TriageRecord triageRecord(@V("ticket") String ticket);
    }

    @Test
    void should_ask_one_question_per_field_in_a_single_call() {

        // given
        FakeDecisionModel model = new FakeDecisionModel(
                Map.of("team", BILLING_ANSWER, "urgent", yesNo(0.9), "refund", yesNo(0.3)));
        SupportDesk supportDesk =
                DecisionServices.builder(SupportDesk.class).decisionModel(model).build();

        // when
        Triage triage = supportDesk.triage("I was charged twice", "enterprise");

        // then
        assertThat(triage.team).isEqualTo(Team.BILLING);
        assertThat(triage.urgent).isTrue();
        assertThat(triage.refund.probability()).isEqualTo(0.3);

        DecisionRequest request = model.request();
        assertThat(request.input()).isEqualTo(Map.of("ticket", "I was charged twice", "plan", "enterprise"));
        assertThat(request.questions()).containsOnlyKeys("team", "urgent", "refund");
        assertThat(request.questions().get("urgent"))
                .isEqualTo(YesNoQuestion.builder()
                        .text("Does this need attention today?")
                        .build());
        assertThat(request.questions().get("refund"))
                .isEqualTo(YesNoQuestion.builder()
                        .text("Does the customer ask for money back?")
                        .build());
    }

    @Test
    void should_support_records_with_thresholds_per_field() {

        // given
        FakeDecisionModel model = new FakeDecisionModel(
                Map.of("team", BILLING_ANSWER, "urgent", yesNo(0.7), "refund", yesNo(0.7)));
        SupportDesk supportDesk = DecisionServices.builder(SupportDesk.class)
                .decisionModel(model)
                .thresholdProvider(context -> context.questionName().equals("urgent") ? 0.8 : null)
                .build();

        // when
        TriageRecord triage = supportDesk.triageRecord("I was charged twice");

        // then
        assertThat(triage.team().value()).isEqualTo(Team.BILLING);
        assertThat(triage.urgent()).isFalse(); // 0.7 < 0.8 (from thresholdProvider)
        assertThat(triage.refund()).isTrue(); // 0.7 >= 0.5 (default)
        assertThat(model.request().input()).isEqualTo(Map.of("ticket", "I was charged twice"));
    }

    // wrappers

    interface RouterWithDetails {

        @Decide("Which team should handle this ticket?")
        DecisionResult<Team> route(@V("ticket") String ticket);

        @Decide("Which team should handle this ticket?")
        CompletableFuture<Team> routeAsync(@V("ticket") String ticket);

        @Decide("Which team should handle this ticket?")
        CompletableFuture<DecisionResult<Choice<Team>>> routeAsyncWithDetails(@V("ticket") String ticket);
    }

    @Test
    void should_return_decision_result() {

        RouterWithDetails router = DecisionServices.builder(RouterWithDetails.class)
                .decisionModel(new FakeDecisionModel(Map.of("route", BILLING_ANSWER)))
                .build();

        DecisionResult<Team> result = router.route("I was charged twice");

        assertThat(result.content()).isEqualTo(Team.BILLING);
        assertThat(result.response().choice("route").value()).isEqualTo(BILLING_ANSWER.value());
        assertThat(result.response().choice("route").probabilities()).isEqualTo(BILLING_ANSWER.probabilities());
        assertThat(result.modelName()).isEqualTo("fake-model");
        assertThat(result.tokenUsage()).isEqualTo(new TokenUsage(10, 2));
    }

    @Test
    void should_call_model_asynchronously() throws Exception {

        // given
        FakeDecisionModel model = new FakeDecisionModel(
                Map.of("routeAsync", BILLING_ANSWER, "routeAsyncWithDetails", BILLING_ANSWER)) {

            @Override
            public DecisionResponse doDecide(DecisionRequest request) {
                throw new AssertionError("sync API must not be called");
            }

            @Override
            public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
                return CompletableFuture.completedFuture(super.doDecide(request));
            }
        };
        RouterWithDetails router = DecisionServices.builder(RouterWithDetails.class)
                .decisionModel(model)
                .build();

        // when-then
        assertThat(router.routeAsync("I was charged twice").get()).isEqualTo(Team.BILLING);
        assertThat(router.routeAsyncWithDetails("I was charged twice").get().content().value())
                .isEqualTo(Team.BILLING);
    }

    interface AsyncSpamFilter {

        @Decide("Is this message spam?")
        CompletableFuture<Boolean> isSpam(@V("message") String message, DecisionRequestParameters parameters);
    }

    @Test
    void async_methods_should_report_invalid_arguments_through_the_future() {

        AsyncSpamFilter spamFilter = DecisionServices.builder(AsyncSpamFilter.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", yesNo(0.7))))
                .build();

        CompletableFuture<Boolean> future = spamFilter.isSpam(null, null);

        assertThat(future)
                .failsWithin(java.time.Duration.ZERO)
                .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    interface CompletionStageRouter {

        @Decide("Which team should handle this ticket?")
        CompletionStage<Team> route(@V("ticket") String ticket);
    }

    @Test
    void should_support_completion_stage() {

        CompletionStageRouter router = DecisionServices.builder(CompletionStageRouter.class)
                .decisionModel(new FakeDecisionModel(Map.of("route", BILLING_ANSWER)))
                .build();

        assertThat(router.route("I was charged twice").toCompletableFuture().join())
                .isEqualTo(Team.BILLING);
    }

    @Test
    void cancelling_the_result_should_cancel_the_model_call() {

        // given
        CompletableFuture<DecisionResponse> modelCall = new CompletableFuture<>();
        FakeDecisionModel model = new FakeDecisionModel(Map.of()) {
            @Override
            public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
                return modelCall;
            }
        };
        RouterWithDetails router = DecisionServices.builder(RouterWithDetails.class)
                .decisionModel(model)
                .build();

        // when
        router.routeAsync("I was charged twice").cancel(true);

        // then
        assertThat(modelCall).isCancelled();
    }

    interface TwoParameters {

        @Decide("Is this message spam?")
        boolean isSpam(@V("subject") String subject, @V("body") String body);
    }

    @Test
    void should_fail_with_clear_message_when_all_arguments_are_null() {

        TwoParameters service = DecisionServices.builder(TwoParameters.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", yesNo(0.7))))
                .build();

        assertThatThrownBy(() -> service.isSpam(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("All arguments of method 'isSpam'")
                .hasMessageContaining("[subject, body]");
    }

    record Customer(String customerPlan, int openTickets) {}

    interface CustomerDesk {

        @Decide("Does this need attention today?")
        boolean urgent(@V("ticket") String ticket, @V("customer") Customer customer);
    }

    @Test
    void should_send_objects_as_maps_with_java_field_names() {

        FakeDecisionModel model = new FakeDecisionModel(Map.of("urgent", yesNo(0.7)));
        CustomerDesk desk = DecisionServices.builder(CustomerDesk.class).decisionModel(model).build();

        desk.urgent("Payouts are failing", new Customer("enterprise", 3));

        assertThat(model.request().input())
                .isEqualTo(Map.of(
                        "ticket", "Payouts are failing",
                        "customer", Map.of("customerPlan", "enterprise", "openTickets", 3)));
    }

    @Test
    void decision_method_should_expose_its_analysis() throws Exception {

        // given
        DecisionMethod method = DecisionMethod.of(SupportDesk.class.getMethod("triageRecord", String.class));

        // then
        assertThat(method.questions()).containsOnlyKeys("team", "urgent", "refund");
        assertThat(method.isAsync()).isFalse();
        assertThat(method.contentType()).isEqualTo(TriageRecord.class);
        assertThat(method.reflectiveTypes()).containsExactlyInAnyOrder(TriageRecord.class, Team.class);

        DecisionRequest request = method.toRequest(new Object[] {"I was charged twice"});
        assertThat(request.input()).isEqualTo(Map.of("ticket", "I was charged twice"));

        TriageRecord triage = (TriageRecord) method.toResult(
                DecisionResponse.builder()
                        .answers(Map.of("team", BILLING_ANSWER, "urgent", yesNo(0.7), "refund", yesNo(0.2)))
                        .build(),
                question -> question.equals("urgent") ? 0.8 : null);
        assertThat(triage.team().value()).isEqualTo(Team.BILLING);
        assertThat(triage.urgent()).isFalse();
        assertThat(triage.refund()).isFalse();
    }

    @Test
    void builder_should_be_extensible_by_frameworks() {

        class FrameworkBuilder<T> extends DecisionServices.Builder<T> {

            FrameworkBuilder(Class<T> serviceInterface) {
                super(serviceInterface);
            }

            @Override
            public T build() {
                assertThat(serviceInterface()).isEqualTo(SpamFilter.class);
                assertThat(decisionModel()).isNotNull();
                return super.build();
            }
        }

        SpamFilter spamFilter = new FrameworkBuilder<>(SpamFilter.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", yesNo(0.7))))
                .build();

        assertThat(spamFilter.isSpam("You won a cruise!")).isTrue();
    }

    @Test
    void threshold_context_should_fall_back_to_requested_model_name() {

        List<String> modelNames = new ArrayList<>();
        FakeDecisionModel model = new FakeDecisionModel(Map.of("isSpam", yesNo(0.7))) {
            @Override
            public DecisionResponse doDecide(DecisionRequest request) {
                return DecisionResponse.builder().answers(answers).build(); // no model name reported
            }
        };
        SpamFilter spamFilter = DecisionServices.builder(SpamFilter.class)
                .decisionModel(model)
                .thresholdProvider(context -> {
                    modelNames.add(context.modelName());
                    return null;
                })
                .build();

        spamFilter.isSpam("You won a cruise!");

        assertThat(modelNames).containsExactly("fake-default-model");
    }

    static class BaseTriage {

        @Decide("Does this need attention today?")
        boolean urgent;
    }

    static class ExtendedTriage extends BaseTriage {

        @Decide("Which team should handle this ticket?")
        Team team;
    }

    interface ExtendedDesk {

        ExtendedTriage triage(@V("ticket") String ticket, DecisionRequestParameters parameters);
    }

    @Test
    void decision_method_should_expose_reflective_and_input_types() throws Exception {

        DecisionMethod method = DecisionMethod.of(
                ExtendedDesk.class.getMethod("triage", String.class, DecisionRequestParameters.class));

        assertThat(method.questions()).containsOnlyKeys("team", "urgent");
        assertThat(method.reflectiveTypes()).containsExactlyInAnyOrder(ExtendedTriage.class, BaseTriage.class, Team.class);
        assertThat(method.inputTypes()).containsExactly(String.class);
    }

    interface LazyRouter {

        @Decide("Which team should handle this ticket?")
        java.util.function.Supplier<Team> route(@V("ticket") String ticket);
    }

    @Test
    void frameworks_should_be_able_to_invoke_methods_with_their_own_async_types() throws Exception {

        // given: a return type unknown to LangChain4j, analyzed as if it returned its type argument
        DecisionMethod method = DecisionMethod.of(LazyRouter.class.getMethod("route", String.class), Team.class);
        FakeDecisionModel model = new FakeDecisionModel(Map.of("route", BILLING_ANSWER));

        // when
        CompletableFuture<Object> result =
                method.invokeAsync(
                        DecisionServiceConfig.builder()
                                .serviceInterface(LazyRouter.class)
                                .decisionModel(model)
                                .build(),
                        new Object[] {"I was charged twice"});

        // then
        assertThat(result.get()).isEqualTo(Team.BILLING);
    }

    @Test
    void builder_should_come_from_decision_services_factory_when_available() {

        DecisionServices.Builder<SpamFilter> builder = DecisionServices.builder(SpamFilter.class);

        assertThat(builder).isInstanceOf(TestDecisionServicesFactory.TestBuilder.class);
    }

    // other methods

    interface ServiceWithDefaultMethod {

        @Decide("Is this message spam?")
        boolean isSpam(@V("message") String message);

        default boolean isHam(String message) {
            return !isSpam(message);
        }
    }

    @Test
    void should_support_default_and_object_methods() {

        ServiceWithDefaultMethod service = DecisionServices.builder(ServiceWithDefaultMethod.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", yesNo(0.1))))
                .build();

        assertThat(service.isHam("Hello")).isTrue();
        assertThat(service).isEqualTo(service).hasSameHashCodeAs(service);
        assertThat(service.toString()).contains("ServiceWithDefaultMethod");
    }

    // configuration errors

    interface MissingDecide {
        boolean isSpam(@V("message") String message);
    }

    interface DecideOnObjectMethod {
        @Decide("Triage the ticket")
        Triage triage(@V("ticket") String ticket);
    }

    interface UnsupportedReturnType {
        @Decide("What is this about?")
        String topic(@V("ticket") String ticket);
    }

    static class UnsupportedField {
        @Decide("What is this about?")
        String topic;
    }

    interface UnsupportedFieldType {
        UnsupportedField analyze(@V("ticket") String ticket);
    }

    static class UnannotatedField {

        @Decide("Is this message spam?")
        boolean spam;

        boolean urgent;
    }

    interface MissingFieldQuestion {
        UnannotatedField analyze(@V("message") String message);
    }

    interface MissingParameterName {
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    interface SeveralRequestParameters {
        @Decide("Is this message spam?")
        boolean isSpam(
                @V("message") String message, DecisionRequestParameters first, DecisionRequestParameters second);
    }

    interface NoInput {
        @Decide("Is this message spam?")
        boolean isSpam(DecisionRequestParameters parameters);
    }

    enum SingleConstant {
        ONLY
    }

    interface BlankQuestion {
        @Decide(" ")
        boolean isSpam(@V("message") String message);
    }

    static class DescriptionOnField {
        @Description("Is this message spam?")
        boolean spam;
    }

    interface DescriptionInsteadOfDecide {
        DescriptionOnField analyze(@V("message") String message);
    }

    static class BaseAnalysis {
        @Decide("Is this message spam?")
        boolean spam;
    }

    static class DuplicateFieldAnalysis extends BaseAnalysis {
        @Decide("Is this message a phishing attempt?")
        boolean spam;
    }

    interface DuplicateFieldNames {
        DuplicateFieldAnalysis analyze(@V("message") String message);
    }

    interface SingleConstantEnum {
        @Decide("Which one?")
        SingleConstant pick(@V("ticket") String ticket);
    }

    @Test
    void should_fail_at_build_time_for_invalid_configuration() {

        FakeDecisionModel model = new FakeDecisionModel(Map.of());

        assertThatThrownBy(() -> DecisionServices.builder(MissingDecide.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("must be annotated with @Decide");

        assertThatThrownBy(() -> DecisionServices.builder(DecideOnObjectMethod.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("annotate the fields of Triage instead");

        assertThatThrownBy(() -> DecisionServices.builder(UnsupportedReturnType.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("unsupported return type: java.lang.String");

        assertThatThrownBy(() -> DecisionServices.builder(UnsupportedFieldType.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("Field 'topic'");

        assertThatThrownBy(() -> DecisionServices.builder(MissingFieldQuestion.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("Field 'urgent' of UnannotatedField")
                .hasMessageContaining("@Decide");

        assertThatThrownBy(() -> DecisionServices.builder(MissingParameterName.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("-parameters")
                .hasMessageContaining("@V");

        assertThatThrownBy(() -> DecisionServices.builder(SeveralRequestParameters.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("several DecisionRequestParameters");

        assertThatThrownBy(() -> DecisionServices.builder(NoInput.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("at least one parameter");

        assertThatThrownBy(() -> DecisionServices.builder(SingleConstantEnum.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("at least 2 constants");

        assertThatThrownBy(() -> DecisionServices.builder(BlankQuestion.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("must not be blank");

        assertThatThrownBy(() -> DecisionServices.builder(DescriptionInsteadOfDecide.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("must be annotated with @Decide");

        assertThatThrownBy(() -> DecisionServices.builder(DuplicateFieldNames.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("several fields named 'spam'");

        assertThatThrownBy(() -> DecisionServices.builder(Triage.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("must be an interface");

        assertThatThrownBy(() -> DecisionServices.builder(SpamFilter.class).build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("DecisionServices.builder(SpamFilter.class).decisionModel(...)");

        assertThatThrownBy(() -> DecisionServices.builder(InnerClassTriageService.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("InnerClassTriage, returned by method 'triage', is an inner class");

        assertThatThrownBy(() -> DecisionServices.builder(FinalFieldTriageService.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("Field 'urgent' of FinalFieldTriage, returned by method 'triage', is final");
    }

    class InnerClassTriage {

        @Decide("Is this urgent?")
        boolean urgent;
    }

    interface InnerClassTriageService {

        InnerClassTriage triage(@V("ticket") String ticket);
    }

    static class FinalFieldTriage {

        @Decide("Is this urgent?")
        final boolean urgent = false;
    }

    interface FinalFieldTriageService {

        FinalFieldTriage triage(@V("ticket") String ticket);
    }
}
