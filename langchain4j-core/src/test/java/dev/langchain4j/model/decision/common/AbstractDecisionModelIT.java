package dev.langchain4j.model.decision.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.decision.listener.DecisionModelErrorContext;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
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

    /**
     * Whether the model rejects a request for a model name it does not know. Some self-hosted servers serve a single
     * model and accept any name.
     */
    protected boolean rejectsUnknownModelNames() {
        return true;
    }

    /**
     * A model name that can be set on a request, overriding the default model name.
     */
    protected abstract String requestModelName();

    private static final YesNoQuestion SPAM =
            YesNoQuestion.builder().text("Is this message spam?").build();

    private static final ChoiceQuestion TEAM = ChoiceQuestion.builder()
            .text("Which team should handle this ticket?")
            .option("billing", "Payments, payouts, charges, invoices, refunds")
            .option("support", "Problems using the product. Not for questions about charges")
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
    void should_answer_choice_question_with_options_without_descriptions() {

        DecisionResponse response = model().decide(DecisionRequest.builder()
                .input("I love this product, it works perfectly!")
                .question(
                        "sentiment",
                        ChoiceQuestion.of("What is the sentiment?", List.of("positive", "negative", "neutral")))
                .build());

        assertThat(response.choice("sentiment").value()).isEqualTo("positive");
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
    void should_place_input_on_scale() {

        ScaleAnswer calm = model().decide(DecisionRequest.builder()
                        .input("Thanks for the quick reply, no rush with the rest.")
                        .question("frustration", FRUSTRATION)
                        .build())
                .scale("frustration");
        ScaleAnswer angry = model().decide(DecisionRequest.builder()
                        .input("This is the THIRD time I write! Nobody answers, this is unacceptable!!!")
                        .question("frustration", FRUSTRATION)
                        .build())
                .scale("frustration");

        assertThat(angry.mean()).isGreaterThan(calm.mean());
    }

    @Test
    void should_take_criteria_into_account() {

        String ticket = "Hi, could you send me a copy of last month's invoice when you have a moment?";

        DecisionResponse strict = model().decide(DecisionRequest.builder()
                .input(ticket)
                .question(
                        "urgent",
                        YesNoQuestion.builder()
                                .text("Is this ticket urgent?")
                                .yesWhen("Only outages and data loss are urgent")
                                .noWhen("Requests for documents such as invoices are never urgent")
                                .build())
                .build());
        DecisionResponse lenient = model().decide(DecisionRequest.builder()
                .input(ticket)
                .question(
                        "urgent",
                        YesNoQuestion.builder()
                                .text("Is this ticket urgent?")
                                .yesWhen("Every request about invoices is urgent")
                                .build())
                .build());

        assertThat(lenient.yesNo("urgent").probability())
                .isGreaterThan(strict.yesNo("urgent").probability());
    }

    @Test
    void should_decide_on_structured_input() {

        DecisionResponse spam = model().decide(DecisionRequest.builder()
                .input(Map.of(
                        "subject", "Congratulations, you won!",
                        "body", "Claim your free cruise now, click here!",
                        "sender", Map.of("known", false)))
                .question("spam", SPAM)
                .build());
        DecisionResponse notSpam = model().decide(DecisionRequest.builder()
                .input(Map.of(
                        "subject", "Meeting tomorrow",
                        "body", "Hi Anna, are we still meeting tomorrow at 10?",
                        "sender", Map.of("known", true)))
                .question("spam", SPAM)
                .build());

        assertThat(spam.yesNo("spam").probability()).isGreaterThan(notSpam.yesNo("spam").probability());
    }

    @Test
    void should_decide_on_non_ascii_input() {

        DecisionResponse spam = model().decide(request("Herzlichen Glückwunsch! Sie haben eine Kreuzfahrt gewonnen, "
                + "klicken Sie hier! 🎉"));
        DecisionResponse notSpam = model().decide(request("Hallo Anna, treffen wir uns morgen um 10 Uhr? – Jörg"));

        assertThat(spam.yesNo("spam").probability()).isGreaterThan(notSpam.yesNo("spam").probability());
    }

    @Test
    void should_use_model_name_from_request() {

        DecisionResponse response = model().decide(DecisionRequest.builder()
                .input("Hi Anna, are we still meeting tomorrow at 10?")
                .question("spam", SPAM)
                .parameters(DecisionRequestParameters.builder()
                        .modelName(requestModelName())
                        .build())
                .build());

        assertThat(response.yesNo("spam").probability()).isBetween(0.0, 1.0);
        assertThat(response.modelName()).isNotBlank();
    }

    @Test
    void should_report_coherent_probabilities() {

        DecisionResponse response = model().decide(DecisionRequest.builder()
                .input("I was charged twice this month and nobody answers my emails!")
                .question("team", TEAM)
                .question("frustration", FRUSTRATION)
                .build());

        ChoiceAnswer team = response.choice("team");
        if (!team.probabilities().isEmpty()) {
            assertThat(team.probabilities()).containsKey(team.value());
            assertThat(team.probabilities().values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(0.05));
            assertThat(team.margin()).isBetween(0.0, 1.0);
        }
        ScaleAnswer frustration = response.scale("frustration");
        if (!frustration.probabilities().isEmpty()) {
            assertThat(frustration.probabilities().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0, within(0.05));
        }
    }

    @Test
    void should_answer_many_questions_in_one_call() {

        DecisionRequest.Builder request =
                DecisionRequest.builder().input("I was charged twice this month and nobody answers my emails!");
        for (int i = 1; i <= 10; i++) {
            request.question("question" + i, YesNoQuestion.of("Does the message mention the number " + i + "?"));
        }

        DecisionResponse response = model().decide(request.build());

        assertThat(response.answers()).hasSize(10);
    }

    @Test
    void should_fail_and_notify_listeners_for_unknown_model_name() {

        assumeTrue(rejectsUnknownModelNames());

        List<Throwable> errors = new CopyOnWriteArrayList<>();
        DecisionModel model = modelWithListener(new DecisionModelListener() {
            @Override
            public void onError(DecisionModelErrorContext context) {
                errors.add(context.error());
            }
        });

        assertThatThrownBy(() -> model.decide(DecisionRequest.builder()
                        .input("Hello")
                        .question("spam", SPAM)
                        .parameters(DecisionRequestParameters.builder()
                                .modelName("no-such-model-lc4j-it")
                                .build())
                        .build()))
                .isInstanceOf(LangChain4jException.class);
        assertThat(errors).singleElement().isInstanceOf(LangChain4jException.class);
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
