package dev.langchain4j.test.quarkus;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.quarkus.Models.useChatModelResponding;
import static dev.langchain4j.test.quarkus.Models.useStreamingChatModel;

import com.example.support.CustomerSupportAssistant;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.test.judge.JudgeCriterion;
import dev.langchain4j.test.judge.DecisionModelJudge;
import dev.langchain4j.test.judge.Judge;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JudgeTest {

    private static final String QUESTION = "Can I return my shoes after two weeks?";
    private static final String ANSWER = "Yes, items can be returned within 30 days of delivery for a full refund.";

    @Inject
    CustomerSupportAssistant assistant;

    @Inject
    DecisionModelJudge supportJudge;

    @Inject
    MockDecisionModel supportJudgeModel;

    private AiServiceInvocation askAboutReturns() {
        useChatModelResponding(AiMessage.from(ANSWER));
        return AiServiceCapture.capture(() -> assistant.chat(QUESTION)).invocation();
    }

    @Test
    void chat_model_judge_passes_satisfied_criteria() {
        AiServiceInvocation invocation = askAboutReturns();
        MockChatModel judgeModel = MockChatModel.thatResponds(
                """
                {"assessments":[
                  {"conditionNumber":1,"reasoning":"It says 30 days.","result":"SATISFIED"},
                  {"conditionNumber":2,"reasoning":"Only claims from the source.","result":"SATISFIED"}]}
                """);

        Judge.from(judgeModel)
                .assertThat(invocation.outputText())
                .given(QUESTION)
                .basedOnContents(invocation.sources())
                .satisfies("says that the return period is 30 days", "contains only claims that are stated in the sources");

        String judgePrompt = judgeModel.request().messages().get(1).toString();
        assertThat(judgePrompt).contains(QUESTION).contains("within 30 days of delivery").contains(ANSWER);
    }

    @Test
    void chat_model_judge_fails_with_reasoning_of_unsatisfied_criterion() {
        AiServiceInvocation invocation = askAboutReturns();
        MockChatModel judgeModel = MockChatModel.thatResponds(
                """
                {"assessments":[
                  {"conditionNumber":1,"reasoning":"The answer does not offer an exchange.","result":"NOT_SATISFIED"}]}
                """);

        assertThatThrownBy(() -> Judge.from(judgeModel)
                        .assertThat(invocation.outputText())
                        .given(QUESTION)
                        .satisfies(JudgeCriterion.from("offers an exchange").notSatisfiedWhen("only a refund is offered")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("offers an exchange")
                .hasMessageContaining("The answer does not offer an exchange.");
    }

    @Test
    void chat_model_judge_with_one_call_per_criterion() {
        AiServiceInvocation invocation = askAboutReturns();
        MockChatModel judgeModel = MockChatModel.thatResponds(
                """
                {"assessments":[{"conditionNumber":1,"reasoning":"ok","result":"SATISFIED"}]}
                """).thenRepeats();

        Judge.from(judgeModel)
                .withOneRequestPerCriterion()
                .assertThat(invocation.outputText())
                .satisfies("is polite", "answers the question");

        assertThat(judgeModel.requests()).hasSize(2);
    }

    @Test
    void decision_model_judge_passes_above_threshold() {
        AiServiceInvocation invocation = askAboutReturns();
        MockDecisionModel judgeModel = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.9);

        Judge.from(judgeModel)
                .withThreshold(0.8)
                .assertThat(invocation.outputText())
                .given(QUESTION)
                .basedOnContents(invocation.sources())
                .satisfies("says that the return period is 30 days");

        assertThat(judgeModel.requests()).hasSize(1);
    }

    @Test
    void decision_model_judge_fails_below_threshold_with_probability() {
        AiServiceInvocation invocation = askAboutReturns();
        MockDecisionModel judgeModel = MockDecisionModel.thatAnswersYesNoQuestions(question -> 0.3);

        assertThatThrownBy(() -> Judge.from(judgeModel)
                        .assertThat(invocation.outputText())
                        .satisfies("mentions free return shipping"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("mentions free return shipping")
                .hasMessageContaining("0.3");
    }

    @Test
    void judges_streamed_answer() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("Returns ", "are ", "free."));
        AiServiceInvocation invocation = AiServiceCapture.capture(() -> assistant.chatStream(QUESTION))
                .invocation();

        Judge.from(MockDecisionModel.thatAnswersYesNoQuestions(question -> 1.0))
                .assertThat(invocation.outputText())
                .basedOnContents(invocation.sources())
                .satisfies("says that returns are free");
    }

    @Test
    void shared_judge_bean_asks_its_own_question() {
        AiServiceInvocation invocation = askAboutReturns();

        supportJudge
                .assertThat(invocation.outputText())
                .given(QUESTION)
                .basedOnContents(invocation.sources())
                .satisfies(
                        JudgeCriterion.from("says that the return period is 30 days"),
                        JudgeCriterion.from("is polite").notSatisfiedWhen("it is rude"));

        DecisionRequest request = supportJudgeModel.lastRequest();
        assertThat(request.questions()).hasSize(2);
        YesNoQuestion first = (YesNoQuestion) request.questions().get("condition1");
        assertThat(first.text())
                .isEqualTo(TestJudges.QUESTION_PREFIX + "says that the return period is 30 days");
        assertThat(first.yesWhen()).isEqualTo("the answer clearly does");
        assertThat(first.noWhen()).isEqualTo("the answer does not, or only partly");
        assertThat(((YesNoQuestion) request.questions().get("condition2")).text())
                .isEqualTo(TestJudges.QUESTION_PREFIX + "is polite");
        assertThat(request.input())
                .isInstanceOfSatisfying(FieldsInput.class, input -> assertThat(input.fields())
                        .containsEntry("userMessage", QUESTION)
                        .containsEntry("response", ANSWER)
                        .containsKey("sources"));
    }

    @Test
    void shared_judge_bean_keeps_its_question_and_threshold() {
        AiServiceInvocation invocation = askAboutReturns();

        assertThatThrownBy(() -> supportJudge.assertThat(invocation.outputText()).satisfies("mentions free return shipping"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("mentions free return shipping")
                .hasMessageContaining("P(yes) = 0.200, threshold = 0.900");
        assertThat(((YesNoQuestion) supportJudgeModel.lastRequest().questions().get("condition1")).text())
                .startsWith(TestJudges.QUESTION_PREFIX);

        assertThatThrownBy(() -> supportJudge
                        .withThreshold(0.99)
                        .assertThat(invocation.outputText())
                        .satisfies("says that the return period is 30 days"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("threshold = 0.990");
        assertThat(((YesNoQuestion) supportJudgeModel.lastRequest().questions().get("condition1")).text())
                .as("withThreshold keeps the custom question")
                .startsWith(TestJudges.QUESTION_PREFIX);
    }
}
