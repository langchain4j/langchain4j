package com.example.support;

import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.assistant.SupportAssistant;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.test.judge.JudgeCriterion;
import dev.langchain4j.test.judge.Judge;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Same configuration as {@link SupportAssistantCaptureTest}, so the Spring context is reused from the cache.
 */
@SpringBootTest
@Import(TestModels.class)
class JudgeTest {

    @Autowired
    SupportAssistant supportAssistant;

    @Test
    void judges_the_captured_answer_with_a_decision_model() {
        String question = "What is your return policy?";
        AiServiceInvocation invocation = capture(() -> supportAssistant.chat(question)).invocation();
        MockDecisionModel judgeModel = MockDecisionModel.thatAnswersYesNoQuestions(
                yesNoQuestion -> yesNoQuestion.text().contains("14 days") ? 0.95 : 0.05);

        Judge.from(judgeModel)
                .assertThat(invocation.outputText())
                .given(question)
                .basedOnContents(invocation.sources())
                .satisfies("says that the car can be returned within 14 days");

        assertThat(((FieldsInput) judgeModel.request().input()).fields().toString())
                .contains("Cars can be returned free of charge within 14 days of the booking.")
                .contains(question);
    }

    @Test
    void names_the_failed_criterion_with_the_probability_of_the_decision_model() {
        String answer = capture(() -> supportAssistant.chat("Hi")).outputText();
        MockDecisionModel judgeModel = MockDecisionModel.thatAnswersYesNoQuestions(
                yesNoQuestion -> yesNoQuestion.text().contains("greets") ? 0.9 : 0.2);

        assertThatThrownBy(() -> Judge.from(judgeModel)
                        .withThreshold(0.8)
                        .assertThat(answer)
                        .satisfies("greets the customer", "mentions the return policy"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("mentions the return policy")
                .hasMessageContaining("P(yes) = 0.200, threshold = 0.800")
                .hasMessageNotContaining("P(yes) = 0.900");
    }

    @Test
    void judges_the_captured_answer_with_a_chat_model() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("Please cancel MS-777")).invocation();
        MockChatModel judgeModel = MockChatModel.thatResponds(
                        """
                        {"assessments": [
                          {"conditionNumber": 1, "reasoning": "It says the booking was cancelled.", "result": "SATISFIED"},
                          {"conditionNumber": 2, "reasoning": "It does not apologize.", "result": "NOT_SATISFIED"}
                        ]}""");

        assertThatThrownBy(() -> Judge.from(judgeModel)
                        .assertThat(invocation.outputText())
                        .given("Please cancel MS-777")
                        .satisfies(
                                JudgeCriterion.from("confirms that booking MS-777 is cancelled"),
                                JudgeCriterion.from("apologizes for the inconvenience")
                                        .satisfiedWhen("it says sorry")
                                        .notSatisfiedWhen("it does not say sorry")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("apologizes for the inconvenience")
                .hasMessageContaining("It does not apologize.");

        String judgePrompt = ((UserMessage) judgeModel.request().messages().get(1)).singleText();
        assertThat(judgePrompt).contains(invocation.outputText()).contains("<passes_when>it says sorry</passes_when>");
    }

    @Test
    void the_judge_model_is_not_captured_as_an_ai_service_invocation() {
        MockChatModel judgeModel = MockChatModel.thatResponds(AiMessage.from(
                        """
                        {"assessments": [{"conditionNumber": 1, "reasoning": "greets", "result": "SATISFIED"}]}"""));

        AiServiceCapture invocations = capture(() -> {
            String answer = supportAssistant.chat("Hi");
            Judge.from(judgeModel).assertThat(answer).satisfies("greets the customer");
            return answer;
        });

        assertThat(invocations.invocations()).hasSize(1);
    }
}
