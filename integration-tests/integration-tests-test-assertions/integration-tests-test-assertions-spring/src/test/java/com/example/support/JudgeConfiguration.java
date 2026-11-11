package com.example.support;

import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.test.mock.MockDecisionModel;
import dev.langchain4j.test.judge.DecisionModelJudge;
import dev.langchain4j.test.judge.Judge;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * A judge with the question of the support team, configured once and injected into the tests that need it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class JudgeConfiguration {

    public static final String QUESTION_PREFIX = "Would a Miles of Smiles support lead accept this response? It must: ";

    @Bean
    MockDecisionModel judgeModel() {
        return MockDecisionModel.thatAnswersYesNoQuestions(
                question -> question.text().contains("14 days") ? 0.95 : 0.1);
    }

    @Bean
    DecisionModelJudge supportLeadJudge(MockDecisionModel judgeModel) {
        return Judge.from(judgeModel).withQuestion(criterion -> YesNoQuestion.builder()
                .text(QUESTION_PREFIX + criterion.text())
                .yesWhen(criterion.satisfiedWhen() != null ? criterion.satisfiedWhen() : "it clearly does")
                .noWhen(criterion.notSatisfiedWhen() != null ? criterion.notSatisfiedWhen() : "it does not, or only partly")
                .build());
    }
}
