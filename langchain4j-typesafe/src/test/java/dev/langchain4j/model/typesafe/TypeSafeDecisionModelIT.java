package dev.langchain4j.model.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.request.ScoreQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.decision.response.ScoreAnswer;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class TypeSafeDecisionModelIT {

    DecisionModel model = TypeSafeDecisionModel.builder()
            .apiKey(System.getenv("TYPESAFE_API_KEY"))
            .modelName("jev-latest")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_answer_all_question_types() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .state("Help! My payouts have been failing for 3 days and nobody answers my emails.")
                .question(
                        "team",
                        ChoiceQuestion.builder()
                                .instructions("Which team should handle this ticket?")
                                .option("billing", "Payments, payouts, invoices, refunds")
                                .option("sales", "Pricing, upgrades, new accounts")
                                .build())
                .question(
                        "urgent",
                        YesNoQuestion.builder()
                                .instructions("Does this need attention today?")
                                .build())
                .question(
                        "frustration",
                        ScoreQuestion.builder()
                                .instructions("How frustrated is the customer?")
                                .level("Calm")
                                .level("Frustrated")
                                .level("Angry")
                                .build())
                .build();

        // when
        DecisionResponse response = model.decide(request);

        // then
        ChoiceAnswer team = response.choice("team");
        assertThat(team.choice()).isEqualTo("billing");
        assertThat(team.probabilities()).containsOnlyKeys("billing", "sales");

        YesNoAnswer urgent = response.yesNo("urgent");
        assertThat(urgent.probability()).isGreaterThan(0.5);

        ScoreAnswer frustration = response.score("frustration");
        assertThat(frustration.score()).isGreaterThan(0.5);
        assertThat(frustration.probabilities()).hasSize(3);

        assertThat(response.modelName()).startsWith("jev");
        assertThat(response.tokenUsage().inputTokenCount()).isPositive();
    }

    @Test
    void should_accept_structured_criteria() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .state(Map.of(
                        "ticket", "I was charged twice for my subscription this month.",
                        "customer_plan", "enterprise"))
                .question(
                        "team",
                        ChoiceQuestion.builder()
                                .instructions("Which team should handle this ticket?")
                                .option(
                                        "billing",
                                        Map.of(
                                                "what", "Payments, charges, invoices, refunds",
                                                "examples", List.of("I was charged twice", "Where is my invoice?")))
                                .option(
                                        "support",
                                        Map.of(
                                                "what", "Problems using the product",
                                                "not_for", "Questions about charges or invoices"))
                                .build())
                .question(
                        "refund",
                        YesNoQuestion.builder()
                                .instructions("Does the customer ask for money back?")
                                .whenTrue("The customer wants a charge reversed or refunded")
                                .whenFalse("The customer only asks what a charge is for")
                                .build())
                .build();

        // when
        DecisionResponse response = model.decide(request);

        // then
        assertThat(response.choice("team").choice()).isEqualTo("billing");
        assertThat(response.yesNo("refund").probability()).isBetween(0.0, 1.0);
    }

    @Test
    void should_decide_async_with_model_name_from_request() throws Exception {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .state("Congratulations! You won a free cruise, click here to claim your prize.")
                .question(
                        "spam",
                        YesNoQuestion.builder().instructions("Is this message spam?").build())
                .parameters(DecisionRequestParameters.builder()
                        .modelName("jev-1.13.0")
                        .build())
                .build();

        // when
        DecisionResponse response = model.decideAsync(request).get();

        // then
        assertThat(response.yesNo("spam").probability()).isGreaterThan(0.5);
        assertThat(response.modelName()).isEqualTo("jev-1.13.0");
    }

    @Test
    void should_fail_with_wrong_api_key() {

        // given
        DecisionModel model = TypeSafeDecisionModel.builder()
                .apiKey("wrong-key")
                .modelName("jev-latest")
                .build();

        DecisionRequest request = DecisionRequest.builder()
                .state("Hello")
                .question("greeting", YesNoQuestion.builder().instructions("Is this a greeting?").build())
                .build();

        // when-then
        assertThatThrownBy(() -> model.decide(request)).isInstanceOf(AuthenticationException.class);
    }
}
