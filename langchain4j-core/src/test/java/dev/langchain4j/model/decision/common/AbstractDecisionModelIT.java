package dev.langchain4j.model.decision.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScoreQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScoreAnswer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Tests that every {@link DecisionModel} implementation must pass.
 */
public abstract class AbstractDecisionModelIT {

    protected abstract DecisionModel model();

    protected abstract DecisionModel modelWithListener(DecisionModelListener listener);

    protected boolean supportsAsync() {
        return true;
    }

    private static final YesNoQuestion SPAM =
            YesNoQuestion.builder().instructions("Is this message spam?").build();

    private static final ChoiceQuestion TEAM = ChoiceQuestion.builder()
            .instructions("Which team should handle this ticket?")
            .option("billing", "Payments, payouts, charges, invoices, refunds")
            .option("support", Map.of("what", "Problems using the product", "not_for", "Questions about charges"))
            .option("sales", "Pricing, upgrades, new accounts")
            .build();

    private static final ScoreQuestion FRUSTRATION = ScoreQuestion.builder()
            .instructions("How frustrated is the customer?")
            .level("Calm")
            .level("Frustrated")
            .level("Angry")
            .build();

    @Test
    void should_answer_yes_no_question() {

        DecisionResponse spam = model().decide(request("Congratulations! You won a free cruise, click here!"));
        DecisionResponse notSpam = model().decide(request("Hi Anna, are we still meeting tomorrow at 10?"));

        assertThat(spam.yesNo("spam").probability()).isGreaterThan(0.5);
        assertThat(notSpam.yesNo("spam").probability()).isLessThan(0.5);
    }

    @Test
    void should_answer_several_questions_in_one_call() {

        DecisionResponse response = model().decide(DecisionRequest.builder()
                .state(Map.of(
                        "ticket", "I was charged twice this month and nobody answers my emails!",
                        "customer", Map.of("plan", "enterprise")))
                .question("team", TEAM)
                .question("frustration", FRUSTRATION)
                .question("spam", SPAM)
                .build());

        ChoiceAnswer team = response.choice("team");
        assertThat(team.value()).isEqualTo("billing");
        if (!team.probabilities().isEmpty()) {
            assertThat(team.probabilities().keySet()).isSubsetOf("billing", "support", "sales");
            assertThat(team.probability("billing")).isGreaterThan(0.5);
        }

        ScoreAnswer frustration = response.score("frustration");
        assertThat(frustration.score()).isBetween(0.5, 2.0);
        if (!frustration.probabilities().isEmpty()) {
            assertThat(frustration.probabilities()).hasSize(3);
        }

        assertThat(response.yesNo("spam").probability()).isLessThan(0.5);
    }

    @Test
    void should_decide_asynchronously() {

        assumeTrue(supportsAsync());

        DecisionResponse response = model().decideAsync(request("Congratulations! You won a free cruise, click here!"))
                .join();

        assertThat(response.yesNo("spam").probability()).isGreaterThan(0.5);
    }

    @Test
    void should_notify_listeners() {

        List<String> events = new CopyOnWriteArrayList<>();
        DecisionModel model = modelWithListener(new DecisionModelListener() {

            @Override
            public void onRequest(DecisionModelRequestContext context) {
                events.add("request");
            }

            @Override
            public void onResponse(DecisionModelResponseContext context) {
                events.add("response:" + context.decisionResponse().answers().keySet());
            }
        });

        model.decide(request("Hello"));

        assertThat(events).containsExactly("request", "response:[spam]");
    }

    private static DecisionRequest request(String message) {
        return DecisionRequest.builder().state(message).question("spam", SPAM).build();
    }
}
