package dev.langchain4j.agentic.patterns.cascade.support;

import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.PRODUCT_REQUEST;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.UNRELATED_REQUEST;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.invokedTiers;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.cascade.support.DeclarativeSupportAgents.DeclarativeSupportCascade;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DeclarativeConfidenceCascadeIT {

    @Test
    void declarative_cascade_answers_a_product_question_at_the_first_tier() {
        DeclarativeSupportCascade cascade = AgenticServices.createAgenticSystem(DeclarativeSupportCascade.class);

        ResultWithAgenticScope<String> result = cascade.ask(PRODUCT_REQUEST);

        assertThat(result.result()).isNotBlank();
        assertThat(invokedTiers(result.agenticScope())).containsExactly("faq");
    }

    @Test
    void declarative_cascade_escalates_an_unrelated_question_to_the_expert() {
        DeclarativeSupportCascade cascade = AgenticServices.createAgenticSystem(DeclarativeSupportCascade.class);

        ResultWithAgenticScope<String> result = cascade.ask(UNRELATED_REQUEST);

        assertThat(result.result()).isNotBlank().doesNotContain("I can only help");
        assertThat(invokedTiers(result.agenticScope())).containsExactly("faq", "expert");
    }
}
