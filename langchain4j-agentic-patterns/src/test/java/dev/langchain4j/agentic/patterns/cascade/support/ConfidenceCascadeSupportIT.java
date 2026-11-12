package dev.langchain4j.agentic.patterns.cascade.support;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.Models.enhancedModel;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.ACCEPTANCE_CRITERION;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.ACCEPTANCE_THRESHOLD;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.PRODUCT_REQUEST;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.UNRELATED_REQUEST;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.decisionModel;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.invokedTiers;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.cascade.ConfidenceCascadePlanner;
import dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.ExpertAgent;
import dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.FaqAgent;
import dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.SupportCascadeWithScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class ConfidenceCascadeSupportIT {

    private final FaqAgent faqAgent =
            AgenticServices.agentBuilder(FaqAgent.class).chatModel(baseModel()).build();
    private final ExpertAgent expertAgent = AgenticServices.agentBuilder(ExpertAgent.class)
            .chatModel(enhancedModel())
            .build();

    private SupportCascadeWithScope cascade(String acceptanceCriterion) {
        return AgenticServices.plannerBuilder(SupportCascadeWithScope.class)
                .subAgents(faqAgent, expertAgent)
                .outputKey("answer")
                .planner(() -> new ConfidenceCascadePlanner(decisionModel(), ACCEPTANCE_THRESHOLD, acceptanceCriterion))
                .build();
    }

    @Test
    void answers_a_product_question_at_the_first_tier() {
        ResultWithAgenticScope<String> result = cascade(null).ask(PRODUCT_REQUEST);

        assertThat(result.result()).isNotBlank();
        assertThat(invokedTiers(result.agenticScope())).containsExactly("faq");
    }

    @Test
    void escalates_an_unrelated_question_to_the_expert() {
        ResultWithAgenticScope<String> result = cascade(null).ask(UNRELATED_REQUEST);

        assertThat(result.result()).isNotBlank().doesNotContain("I can only help");
        assertThat(invokedTiers(result.agenticScope())).containsExactly("faq", "expert");
    }

    @Test
    void answers_a_product_question_at_the_first_tier_with_an_acceptance_criterion() {
        ResultWithAgenticScope<String> result = cascade(ACCEPTANCE_CRITERION).ask(PRODUCT_REQUEST);

        assertThat(result.result()).isNotBlank();
        assertThat(invokedTiers(result.agenticScope())).containsExactly("faq");
    }

    @Test
    void escalates_an_unrelated_question_to_the_expert_with_an_acceptance_criterion() {
        ResultWithAgenticScope<String> result = cascade(ACCEPTANCE_CRITERION).ask(UNRELATED_REQUEST);

        assertThat(result.result()).isNotBlank().doesNotContain("I can only help");
        assertThat(invokedTiers(result.agenticScope())).containsExactly("faq", "expert");
    }
}
