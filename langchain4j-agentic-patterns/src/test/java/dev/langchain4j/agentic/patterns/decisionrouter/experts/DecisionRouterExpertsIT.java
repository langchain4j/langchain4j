package dev.langchain4j.agentic.patterns.decisionrouter.experts;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.LEGAL_REQUEST;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.MEDICAL_AND_LEGAL_REQUEST;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.MEDICAL_REQUEST;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.TECHNICAL_REQUEST;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.UNRELATED_REQUEST;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.decisionModel;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.invokedExperts;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.ExpertRouterWithScope;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.LegalExpert;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.MedicalExpert;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.MultiExpertRouterWithScope;
import dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.TechnicalExpert;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionRouterExpertsIT {

    private final MedicalExpert medicalExpert = AgenticServices.agentBuilder(MedicalExpert.class)
            .chatModel(baseModel())
            .build();
    private final LegalExpert legalExpert = AgenticServices.agentBuilder(LegalExpert.class)
            .chatModel(baseModel())
            .build();
    private final TechnicalExpert technicalExpert = AgenticServices.agentBuilder(TechnicalExpert.class)
            .chatModel(baseModel())
            .build();

    private ExpertRouterWithScope router() {
        return AgenticServices.plannerBuilder(ExpertRouterWithScope.class)
                .subAgents(medicalExpert, legalExpert, technicalExpert)
                .outputKey("response")
                .planner(() -> new DecisionRouterPlanner(decisionModel()))
                .build();
    }

    private MultiExpertRouterWithScope multiRouter(double activationThreshold) {
        return AgenticServices.plannerBuilder(MultiExpertRouterWithScope.class)
                .subAgents(medicalExpert, legalExpert, technicalExpert)
                .outputKey("responses")
                .planner(() -> new DecisionRouterPlanner(decisionModel(), activationThreshold))
                .build();
    }

    @Test
    void routes_medical_request_to_medical_expert() {
        assertRoutedTo(MEDICAL_REQUEST, "medical");
    }

    @Test
    void routes_legal_request_to_legal_expert() {
        assertRoutedTo(LEGAL_REQUEST, "legal");
    }

    @Test
    void routes_technical_request_to_technical_expert() {
        assertRoutedTo(TECHNICAL_REQUEST, "technical");
    }

    private void assertRoutedTo(String request, String expert) {
        ResultWithAgenticScope<String> result = router().ask(request);

        assertThat(result.result()).isNotBlank();
        assertThat(invokedExperts(result.agenticScope())).containsExactly(expert);
    }

    @Test
    void activates_only_the_relevant_expert() {
        ResultWithAgenticScope<Map<String, String>> result = multiRouter(0.5).ask(MEDICAL_REQUEST);

        assertThat(result.result()).containsOnlyKeys("medical");
        assertThat(result.result().get("medical")).isNotBlank();
        assertThat(invokedExperts(result.agenticScope())).containsExactly("medical");
    }

    @Test
    void activates_all_the_relevant_experts() {
        ResultWithAgenticScope<Map<String, String>> result = multiRouter(0.5).ask(MEDICAL_AND_LEGAL_REQUEST);

        assertThat(result.result()).containsOnlyKeys("medical", "legal");
        assertThat(result.result().values())
                .allSatisfy(response -> assertThat(response).isNotBlank());
        assertThat(invokedExperts(result.agenticScope())).containsExactly("medical", "legal");
    }

    @Test
    void activates_no_expert_for_an_unrelated_request() {
        ResultWithAgenticScope<Map<String, String>> result = multiRouter(0.5).ask(UNRELATED_REQUEST);

        assertThat(result.result()).isEmpty();
        assertThat(invokedExperts(result.agenticScope())).isEmpty();
    }
}
