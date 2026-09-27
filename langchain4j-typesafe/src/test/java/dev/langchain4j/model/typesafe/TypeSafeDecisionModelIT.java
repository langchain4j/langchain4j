package dev.langchain4j.model.typesafe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.NoulQuestion;
import dev.langchain4j.model.decision.request.ScoreQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.NoulAnswer;
import dev.langchain4j.model.decision.response.ScoreAnswer;
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
                        NoulQuestion.builder()
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

        NoulAnswer urgent = response.noul("urgent");
        assertThat(urgent.probability()).isGreaterThan(0.5);

        ScoreAnswer frustration = response.score("frustration");
        assertThat(frustration.score()).isGreaterThan(0.5);
        assertThat(frustration.probabilities()).hasSize(3);

        assertThat(response.modelName()).startsWith("jev");
        assertThat(response.tokenUsage().inputTokenCount()).isPositive();
    }
}
