package dev.langchain4j.test.quarkus;

import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.test.judge.DecisionModelJudge;
import dev.langchain4j.test.judge.Judge;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Provides the judge shared by all tests: a decision model asked a support-specific yes/no question per criterion.
 */
public class TestJudges {

    static final String QUESTION_PREFIX = "Would a support team lead accept this answer to a customer? It must: ";

    @Produces
    @Singleton
    MockDecisionModel judgeModel() {
        // "yes" unless the criterion asks for something the support answers never do
        return MockDecisionModel.thatAnswersYesNoQuestions(
                        question -> question.text().contains("free return shipping") ? 0.2 : 0.95)
                .thenRepeats(); // a singleton shared by the tests
    }

    @Produces
    @Singleton
    DecisionModelJudge supportJudge(MockDecisionModel judgeModel) {
        return Judge.from(judgeModel)
                .withQuestion(criterion -> YesNoQuestion.builder()
                        .text(QUESTION_PREFIX + criterion.text())
                        .yesWhen("the answer clearly does")
                        .noWhen("the answer does not, or only partly")
                        .build())
                .withThreshold(0.9);
    }
}
