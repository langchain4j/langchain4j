package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.common.AbstractDecisionModelIT;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiDecisionModelIT extends AbstractDecisionModelIT {

    private static final String MODEL_NAME = "gemini-decision-1";

    static GoogleAiGeminiDecisionModel.GoogleAiGeminiDecisionModelBuilder builder() {
        return GoogleAiGeminiDecisionModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName(MODEL_NAME);
    }

    @Override
    protected DecisionModel model() {
        return builder().logRequests(true).logResponses(true).build();
    }

    @Override
    protected DecisionModel modelWithListener(DecisionModelListener listener) {
        return builder().listeners(listener).build();
    }

    @Override
    protected boolean reportsProbabilities() {
        return true;
    }

    @Override
    protected boolean reportsTokenUsage() {
        return true;
    }

    @Override
    protected String requestModelName() {
        return MODEL_NAME;
    }

    @Test
    void should_map_unknown_model_to_model_not_found_exception() {

        DecisionRequest request = DecisionRequest.builder()
                .input("Hello")
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .parameters(DecisionRequestParameters.builder()
                        .modelName("no-such-model-lc4j-it")
                        .build())
                .build();

        assertThatThrownBy(() -> model().decide(request)).isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void should_report_gemini_validation_message() {

        DecisionRequest request = DecisionRequest.builder()
                .input("Hello")
                .question(
                        "team",
                        ChoiceQuestion.builder()
                                .text("Which team should handle this?")
                                .option("a".repeat(129))
                                .option("support")
                                .build())
                .build();

        assertThatThrownBy(() -> model().decide(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("128");
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
