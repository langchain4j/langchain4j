package dev.langchain4j.decision.it;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.decision.DecisionServices;
import dev.langchain4j.service.decision.Scale;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class DecisionServicesIT {

    enum Team {
        @Description("Payments, payouts, charges, invoices, refunds")
        BILLING,
        @Description("Problems using the product, bugs, how-to questions")
        SUPPORT,
        @Description("Pricing, upgrades, new accounts")
        SALES
    }

    enum Severity {
        @Description("Cosmetic issue, no impact on customers")
        LOW,
        @Description("A feature is degraded for some customers, a workaround exists")
        MEDIUM,
        @Description("Outage or data loss for many customers")
        HIGH
    }

    record Triage(
            @Decide("Which team should handle this ticket?") Team team,
            @Decide("Does this need attention today?") boolean urgent,
            @Decide("Does the customer ask for money back?") YesNoAnswer refund) {}

    interface SupportDesk {

        Triage triage(@V("ticket") String ticket, @V("customer_plan") String plan);

        @Decide("Is this message spam?")
        boolean isSpam(@V("message") String message);

        @Decide("How severe is this incident?")
        Scale<Severity> severity(@V("incident") String incident);

        @Decide("Which team should handle this ticket?")
        DecisionResult<Choice<Team>> route(@V("ticket") String ticket);

        @Decide("Which team should handle this ticket?")
        CompletableFuture<Team> routeAsync(@V("ticket") String ticket);

        @Decide("Which team should handle this ticket?")
        DecisionResult<Team> route(@V("ticket") String ticket, DecisionRequestParameters parameters);
    }

    DecisionModel decisionModel = TypeSafeDecisionModel.builder()
            .apiKey(System.getenv("TYPESAFE_API_KEY"))
            .modelName("jev-latest")
            .logRequests(true)
            .logResponses(true)
            .build();

    SupportDesk supportDesk = DecisionServices.builder(SupportDesk.class)
            .decisionModel(decisionModel)
            .build();

    @Test
    void should_answer_several_questions_in_one_call() {

        Triage triage = supportDesk.triage(
                "I was charged twice for my subscription this month and I want my money back. "
                        + "Our payroll run is today!",
                "enterprise");

        assertThat(triage.team()).isEqualTo(Team.BILLING);
        assertThat(triage.urgent()).isTrue();
        assertThat(triage.refund().probability()).isGreaterThan(0.5);
    }

    @Test
    void should_answer_yes_no_question() {

        String spam = "Congratulations! You won a free cruise, click here to claim your prize.";

        assertThat(supportDesk.isSpam(spam)).isTrue();
        assertThat(supportDesk.isSpam("Hi Anna, are we still meeting tomorrow at 10?"))
                .isFalse();
    }

    @Test
    void should_place_input_on_a_scale() {

        Scale<Severity> outage = supportDesk.severity("The whole platform is down and customers lost their data.");
        Scale<Severity> typo = supportDesk.severity("There is a typo in the footer of the pricing page.");

        assertThat(outage.mostLikely()).isEqualTo(Severity.HIGH);
        assertThat(typo.mostLikely()).isEqualTo(Severity.LOW);
        assertThat(outage.mean()).isGreaterThan(typo.mean());
        assertThat(outage.probabilityAtLeast(Severity.MEDIUM)).isGreaterThan(0.5);
    }

    @Test
    void should_return_choice_with_probabilities_and_metadata() {

        DecisionResult<Choice<Team>> result = supportDesk.route("The app crashes when I open the settings page.");

        assertThat(result.content().value()).isEqualTo(Team.SUPPORT);
        assertThat(result.content().probabilities()).containsOnlyKeys(Team.BILLING, Team.SUPPORT, Team.SALES);
        assertThat(result.modelName()).startsWith("jev");
        assertThat(result.tokenUsage().inputTokenCount()).isPositive();
    }

    @Test
    void should_decide_asynchronously() throws Exception {

        Team team = supportDesk.routeAsync("How much does the premium plan cost?").get();

        assertThat(team).isEqualTo(Team.SALES);
    }

    @Test
    void should_use_model_name_from_request_parameters() {

        // pinned only to check that the model name is passed through; bump it when TypeSafe retires this version
        DecisionResult<Team> result = supportDesk.route(
                "Where is my invoice for September?",
                DecisionRequestParameters.builder().modelName("jev-1.13.0").build());

        assertThat(result.content()).isEqualTo(Team.BILLING);
        assertThat(result.modelName()).isEqualTo("jev-1.13.0");
    }
}
