package com.example.support;

import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.assistant.SupportAssistant;
import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.test.judge.JudgeCriterion;
import dev.langchain4j.test.judge.DecisionModelJudge;
import dev.langchain4j.test.service.AiServiceCapture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestModels.class, JudgeConfiguration.class})
class SharedJudgeBeanTest {

    @Autowired
    SupportAssistant supportAssistant;

    @Autowired
    DecisionModelJudge supportLeadJudge;

    @Autowired
    MockDecisionModel judgeModel;

    @Test
    void judges_with_the_question_of_the_injected_judge() {
        String userMessage = "What is your return policy?";
        AiServiceCapture capture = capture(() -> supportAssistant.chat(userMessage));

        supportLeadJudge
                .assertThat(capture.outputText())
                .given(userMessage)
                .basedOnContents(capture.invocation().sources())
                .satisfies(JudgeCriterion.from("says that the car can be returned within 14 days")
                        .satisfiedWhen("it names the 14 days"));

        Question question = lastJudgeRequest().questions().values().iterator().next();
        assertThat(question).isInstanceOf(YesNoQuestion.class);
        YesNoQuestion yesNoQuestion = (YesNoQuestion) question;
        assertThat(yesNoQuestion.text())
                .isEqualTo(JudgeConfiguration.QUESTION_PREFIX + "says that the car can be returned within 14 days");
        assertThat(yesNoQuestion.yesWhen()).isEqualTo("it names the 14 days");
        assertThat(yesNoQuestion.noWhen()).isEqualTo("it does not, or only partly");
        assertThat(((FieldsInput) lastJudgeRequest().input()).fields().toString())
                .contains(userMessage)
                .contains(SupportScript.POLICY_ANSWER)
                .contains("Cars can be returned free of charge within 14 days of the booking.");
    }

    @Test
    void with_threshold_keeps_the_question_of_the_injected_judge() {
        String answer = capture(() -> supportAssistant.chat("What is your return policy?")).outputText();

        assertThatThrownBy(() -> supportLeadJudge
                        .withThreshold(0.99)
                        .assertThat(answer)
                        .satisfies("says that the car can be returned within 14 days"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("P(yes) = 0.950, threshold = 0.990");

        YesNoQuestion question = (YesNoQuestion) lastJudgeRequest().questions().values().iterator().next();
        assertThat(question.text()).startsWith(JudgeConfiguration.QUESTION_PREFIX);
    }

    private DecisionRequest lastJudgeRequest() {
        // the judge model is a bean shared by the tests of this class
        return judgeModel.requests().get(judgeModel.requests().size() - 1);
    }
}
