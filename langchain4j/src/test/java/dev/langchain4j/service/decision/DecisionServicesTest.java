package dev.langchain4j.service.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.NoulQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.NoulAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

        DecisionRequest request() {
            assertThat(requests).hasSize(1);
            return requests.get(0);
        }
    }

    private static NoulAnswer noul(double probability) {
        return NoulAnswer.builder().probability(probability).build();
    }

    private static final ChoiceAnswer BILLING_ANSWER = ChoiceAnswer.builder()
            .choice("BILLING")
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
        FakeDecisionModel model = new FakeDecisionModel(Map.of("isSpam", noul(0.7)));
        SpamFilter spamFilter =
                DecisionServices.builder(SpamFilter.class).decisionModel(model).build();

        // when
        boolean spam = spamFilter.isSpam("You won a cruise!");

        // then
        assertThat(spam).isTrue();
        assertThat(model.request().state()).isEqualTo(Map.of("message", "You won a cruise!"));
        assertThat(model.request().questions())
                .isEqualTo(Map.of(
                        "isSpam",
                        NoulQuestion.builder()
                                .instructions("Is this message spam?")
                                .build()));
    }

    @Test
    void should_use_default_threshold_of_0_5() {

        SpamFilter spamFilter = DecisionServices.builder(SpamFilter.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", noul(0.49))))
                .build();

        assertThat(spamFilter.isSpam("Hello")).isFalse();
    }

    @Test
    void should_use_threshold_provider() {

        // given
        List<String> requestedThresholds = new ArrayList<>();
        Map<String, Double> config = new java.util.HashMap<>(Map.of("isSpam", 0.9));
        SpamFilter spamFilter = DecisionServices.builder(SpamFilter.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", noul(0.7))))
                .thresholdProvider(question -> {
                    requestedThresholds.add(question);
                    return config.get(question);
                })
                .build();

        // when-then
        assertThat(spamFilter.isSpam("You won a cruise!")).isFalse();

        config.put("isSpam", 0.6); // e.g. configuration changed at runtime
        assertThat(spamFilter.isSpam("You won a cruise!")).isTrue();

        config.remove("isSpam"); // falls back to 0.5
        assertThat(spamFilter.isSpam("You won a cruise!")).isTrue();

        assertThat(requestedThresholds).containsExactly("isSpam", "isSpam", "isSpam");
    }

    @Test
    void should_reject_invalid_threshold() {

        SpamFilter spamFilter = DecisionServices.builder(SpamFilter.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", noul(0.7))))
                .thresholdProvider(question -> 1.5)
                .build();

        assertThatThrownBy(() -> spamFilter.isSpam("Hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    interface SpamScorer {

        @Decide("Is this message spam?")
        YesNo isSpam(@V("message") String message);
    }

    @Test
    void should_return_yes_no() {

        SpamScorer spamScorer = DecisionServices.builder(SpamScorer.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", noul(0.7))))
                .build();

        YesNo spam = spamScorer.isSpam("You won a cruise!");

        assertThat(spam.probability()).isEqualTo(0.7);
        assertThat(spam.isYes(0.6)).isTrue();
        assertThat(spam.isYes(0.8)).isFalse();
    }

    interface SpamFilterWithParameters {

        @Decide("Is this message spam?")
        boolean isSpam(@V("message") String message, DecisionRequestParameters parameters);
    }

    @Test
    void should_pass_request_parameters_and_not_send_them_as_state() {

        // given
        FakeDecisionModel model = new FakeDecisionModel(Map.of("isSpam", noul(0.7)));
        SpamFilterWithParameters spamFilter = DecisionServices.builder(SpamFilterWithParameters.class)
                .decisionModel(model)
                .build();

        // when
        spamFilter.isSpam(
                "You won a cruise!",
                DecisionRequestParameters.builder().modelName("jev-1.13.0").build());

        // then
        assertThat(model.request().modelName()).isEqualTo("jev-1.13.0");
        assertThat(model.request().state()).isEqualTo(Map.of("message", "You won a cruise!"));
    }

    @Test
    void should_reject_null_request_parameters() {

        SpamFilterWithParameters spamFilter = DecisionServices.builder(SpamFilterWithParameters.class)
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", noul(0.7))))
                .build();

        assertThatThrownBy(() -> spamFilter.isSpam("Hello", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DecisionRequestParameters");
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
                                .instructions("Which team should handle this ticket?")
                                .option("BILLING", "Payments, invoices, refunds")
                                .option("SUPPORT", "Problems using the product")
                                .option("SALES", "SALES")
                                .build()));
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

        Choice<Team> choice = new Choice<>(Team.BILLING, Map.of(Team.BILLING, 0.55, Team.SUPPORT, 0.4), null);

        assertThat(choice.probability(Team.BILLING)).isEqualTo(0.55);
        assertThat(choice.probability(Team.SALES)).isZero();
        assertThat(choice.margin()).isCloseTo(0.15, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void choice_should_support_options_that_are_not_enum_constants() {

        Choice<String> choice = new Choice<>("refunds-agent", Map.of("refunds-agent", 0.7, "billing-agent", 0.3), 0.4);

        assertThat(choice.value()).isEqualTo("refunds-agent");
        assertThat(choice.probability("billing-agent")).isEqualTo(0.3);
        assertThat(choice.margin()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void choice_should_fail_when_model_did_not_report_probabilities() {

        Choice<Team> choice = new Choice<>(Team.BILLING, Map.of(), null);

        assertThatThrownBy(() -> choice.probability(Team.BILLING)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(choice::margin).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_fail_when_model_chooses_unknown_option() {

        Router router = DecisionServices.builder(Router.class)
                .decisionModel(new FakeDecisionModel(Map.of(
                        "route", ChoiceAnswer.builder().choice("MARKETING").build())))
                .build();

        assertThatThrownBy(() -> router.route("Hello"))
                .isInstanceOf(LangChain4jException.class)
                .hasMessageContaining("MARKETING");
    }

    // objects with several questions

    static class Triage {

        @Decide("Which team should handle this ticket?")
        Team team;

        @Description("Does this need attention today?")
        boolean urgent;

        YesNo refund;
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
                Map.of("team", BILLING_ANSWER, "urgent", noul(0.9), "refund", noul(0.3)));
        SupportDesk supportDesk =
                DecisionServices.builder(SupportDesk.class).decisionModel(model).build();

        // when
        Triage triage = supportDesk.triage("I was charged twice", "enterprise");

        // then
        assertThat(triage.team).isEqualTo(Team.BILLING);
        assertThat(triage.urgent).isTrue();
        assertThat(triage.refund.probability()).isEqualTo(0.3);

        DecisionRequest request = model.request();
        assertThat(request.state()).isEqualTo(Map.of("ticket", "I was charged twice", "plan", "enterprise"));
        assertThat(request.questions()).containsOnlyKeys("team", "urgent", "refund");
        assertThat(request.questions().get("urgent"))
                .isEqualTo(NoulQuestion.builder()
                        .instructions("Does this need attention today?")
                        .build());
        assertThat(request.questions().get("refund"))
                .isEqualTo(NoulQuestion.builder().instructions("refund").build());
    }

    @Test
    void should_support_records_with_thresholds_per_field() {

        // given
        FakeDecisionModel model = new FakeDecisionModel(
                Map.of("team", BILLING_ANSWER, "urgent", noul(0.7), "refund", noul(0.7)));
        SupportDesk supportDesk = DecisionServices.builder(SupportDesk.class)
                .decisionModel(model)
                .thresholdProvider(question -> question.equals("urgent") ? 0.8 : null)
                .build();

        // when
        TriageRecord triage = supportDesk.triageRecord("I was charged twice");

        // then
        assertThat(triage.team().value()).isEqualTo(Team.BILLING);
        assertThat(triage.urgent()).isFalse(); // 0.7 < 0.8 (from thresholdProvider)
        assertThat(triage.refund()).isTrue(); // 0.7 >= 0.5 (default)
        assertThat(model.request().state()).isEqualTo(Map.of("ticket", "I was charged twice"));
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
        assertThat(result.response().choice("route")).isEqualTo(BILLING_ANSWER);
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
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", noul(0.7))))
                .build();

        CompletableFuture<Boolean> future = spamFilter.isSpam("Hello", null);

        assertThat(future)
                .failsWithin(java.time.Duration.ZERO)
                .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                .withCauseInstanceOf(IllegalArgumentException.class);
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
                .decisionModel(new FakeDecisionModel(Map.of("isSpam", noul(0.1))))
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

    interface MissingParameterName {
        @Decide("Is this message spam?")
        boolean isSpam(String message);
    }

    interface SeveralRequestParameters {
        @Decide("Is this message spam?")
        boolean isSpam(
                @V("message") String message, DecisionRequestParameters first, DecisionRequestParameters second);
    }

    interface NoState {
        @Decide("Is this message spam?")
        boolean isSpam(DecisionRequestParameters parameters);
    }

    enum SingleConstant {
        ONLY
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

        assertThatThrownBy(() -> DecisionServices.builder(NoState.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("at least one parameter");

        assertThatThrownBy(() -> DecisionServices.builder(SingleConstantEnum.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("at least 2 constants");

        assertThatThrownBy(() -> DecisionServices.builder(Triage.class)
                        .decisionModel(model)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("must be an interface");

        assertThatThrownBy(() -> DecisionServices.builder(SpamFilter.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisionModel");
    }
}
