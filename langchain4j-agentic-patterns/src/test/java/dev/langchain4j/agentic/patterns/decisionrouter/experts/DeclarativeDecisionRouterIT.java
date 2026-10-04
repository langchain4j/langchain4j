package dev.langchain4j.agentic.patterns.decisionrouter.experts;

import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.LEGAL_REQUEST;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.MEDICAL_AND_LEGAL_REQUEST;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.invokedExperts;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.DeclarativeExpertAgents.DeclarativeExpertRouter;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.DeclarativeExpertAgents.DeclarativeMultiExpertRouter;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DeclarativeDecisionRouterIT {

    @Test
    void declarative_router_invokes_the_most_probable_expert() {
        DeclarativeExpertRouter router = AgenticServices.createAgenticSystem(DeclarativeExpertRouter.class);

        ResultWithAgenticScope<String> result = router.ask(LEGAL_REQUEST);

        assertThat(result.result()).isNotBlank();
        assertThat(invokedExperts(result.agenticScope())).containsExactly("legal");
    }

    @Test
    void declarative_router_invokes_all_the_experts_reaching_the_threshold() {
        DeclarativeMultiExpertRouter router = AgenticServices.createAgenticSystem(DeclarativeMultiExpertRouter.class);

        ResultWithAgenticScope<Map<String, String>> result = router.ask(MEDICAL_AND_LEGAL_REQUEST);

        assertThat(result.result()).containsOnlyKeys("medical", "legal");
        assertThat(invokedExperts(result.agenticScope())).containsExactly("medical", "legal");
    }
}
