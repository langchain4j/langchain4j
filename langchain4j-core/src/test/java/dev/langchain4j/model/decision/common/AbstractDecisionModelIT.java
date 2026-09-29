package dev.langchain4j.model.decision.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
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

    /**
     * Whether the model reports the probabilities of choice and scale answers. When it does, the test fails if they
     * are missing, instead of skipping the assertions about them.
     */
    protected boolean reportsProbabilities() {
        return false;
    }

    protected boolean reportsTokenUsage() {
        return false;
    }

    private static final YesNoQuestion SPAM =
            YesNoQuestion.builder().text("Is this message spam?").build();

    private static final ChoiceQuestion TEAM = ChoiceQuestion.builder()
            .text("Which team should handle this ticket?")
            .option("billing", "Payments, payouts, charges, invoices, refunds")
            .option("support", Map.of("what", "Problems using the product", "not_for", "Questions about charges"))
            .option("sales", "Pricing, upgrades, new accounts")
            .build();

    private static final ScaleQuestion FRUSTRATION = ScaleQuestion.builder()
            .text("How frustrated is the customer?")
            .level("Calm")
            .level("Frustrated")
            .level("Angry")
            .build();

    @Test
    void should_answer_yes_no_question() {

        DecisionResponse spam = model().decide(request("Congratulations! You won a free cruise, click here!"));
        DecisionResponse notSpam = model().decide(request("Hi Anna, are we still meeting tomorrow at 10?"));

        // relative assertions, so that models of different sizes and calibrations pass
        assertThat(spam.yesNo("spam").probability()).isGreaterThan(notSpam.yesNo("spam").probability());
    }

    @Test
    void should_answer_several_questions_in_one_call() {

        DecisionResponse response = model().decide(DecisionRequest.builder()
                .input(Map.of(
                        "ticket", "I was charged twice this month and nobody answers my emails!",
                        "customer", Map.of("plan", "enterprise")))
                .question("team", TEAM)
                .question("frustration", FRUSTRATION)
                .question("spam", SPAM)
                .build());

        ChoiceAnswer team = response.choice("team");
        assertThat(team.value()).isEqualTo("billing");
        if (reportsProbabilities()) {
            assertThat(team.probabilities()).isNotEmpty();
            assertThat(response.scale("frustration").probabilities()).isNotEmpty();
        }
        if (reportsTokenUsage()) {
            assertThat(response.tokenUsage()).isNotNull();
        }
        if (!team.probabilities().isEmpty()) {
            assertThat(team.probabilities().keySet()).isSubsetOf("billing", "support", "sales");
            assertThat(team.probabilityOf("billing"))
                    .isGreaterThan(team.probabilityOf("support"))
                    .isGreaterThan(team.probabilityOf("sales"));
        }

        ScaleAnswer frustration = response.scale("frustration");
        assertThat(frustration.mean()).isBetween(0.0, 2.0);
        if (!frustration.probabilities().isEmpty()) {
            assertThat(frustration.probabilities()).hasSize(3);
        }

        assertThat(response.yesNo("spam").probability()).isBetween(0.0, 1.0);
    }

    @Test
    void should_decide_asynchronously() {

        assumeTrue(supportsAsync());

        DecisionResponse spam = model().decideAsync(request("Congratulations! You won a free cruise, click here!"))
                .join();
        DecisionResponse notSpam = model().decideAsync(request("Hi Anna, are we still meeting tomorrow at 10?"))
                .join();

        assertThat(spam.yesNo("spam").probability()).isGreaterThan(notSpam.yesNo("spam").probability());
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
        return DecisionRequest.builder().input(message).question("spam", SPAM).build();
    }
}
