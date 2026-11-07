package dev.langchain4j.agentic.patterns.blackboard;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.DiagnosisAgent;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.LabResultAnalyzer;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.MedicalDiagnostics;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.SymptomExtractor;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A decision agent as a sensing knowledge source of a blackboard: it fires as soon as the lab analysis is on the
 * board, and its boolean answer gates the specialist.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionSensingBlackboardIT {

    public interface NephrologyReferral {

        @Decide("""
                Do these lab results call for a review by a nephrologist?
                Yes: signs of impaired kidney function, such as elevated creatinine or BUN, or proteinuria.
                No: kidney markers within the normal range.""")
        @Agent(value = "Decides whether the lab analysis calls for a nephrologist", outputKey = "nephrologistNeeded")
        boolean decide(@V("labAnalysis") String labAnalysis);
    }

    public interface Nephrologist {

        @UserMessage("""
                You are a nephrologist.
                Given these symptoms: {{symptoms}}
                and this lab analysis: {{labAnalysis}}
                write a short specialist assessment of the kidney function.
                """)
        @Agent(value = "A nephrologist assessing the kidney function", outputKey = "nephrologyAssessment")
        String assess(
                @V("symptoms") String symptoms,
                @V("labAnalysis") String labAnalysis,
                @V("nephrologistNeeded") boolean nephrologistNeeded);
    }

    private static DecisionModel decisionModel() {
        return TypeSafeDecisionModel.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .modelName("jev-1.13.0")
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Test
    void sensing_agent_fires_on_the_lab_analysis_and_gates_the_specialist() {
        SymptomExtractor symptomExtractor = AgenticServices.agentBuilder(SymptomExtractor.class)
                .chatModel(baseModel())
                .build();
        LabResultAnalyzer labAnalyzer = AgenticServices.agentBuilder(LabResultAnalyzer.class)
                .chatModel(baseModel())
                .build();
        NephrologyReferral referral = AgenticServices.decisionAgentBuilder(NephrologyReferral.class)
                .decisionModel(decisionModel())
                .build();
        Nephrologist nephrologist = AgenticServices.agentBuilder(Nephrologist.class)
                .chatModel(baseModel())
                .activationCondition("a nephrologist is needed", scope -> scope.readState("nephrologistNeeded", false))
                .build();
        DiagnosisAgent diagnosis = AgenticServices.agentBuilder(DiagnosisAgent.class)
                .chatModel(baseModel())
                .build();

        MedicalDiagnostics diagnostics = AgenticServices.plannerBuilder(MedicalDiagnostics.class)
                .subAgents(symptomExtractor, labAnalyzer, referral, nephrologist, diagnosis)
                .planner(BlackboardPlanner::new)
                .outputKey("diagnosis")
                .build();

        String result = diagnostics.diagnose(
                "Patient reports persistent headaches, dizziness, and blurred vision for the past two weeks. "
                        + "Blood pressure measured at 180/110 mmHg.",
                "CBC: normal. BMP: elevated creatinine (2.1 mg/dL), elevated BUN (35 mg/dL). "
                        + "Urinalysis: proteinuria detected.",
                "Lisinopril 10mg daily, Metformin 500mg twice daily");

        assertThat(result).isNotBlank();
        AgenticScope scope =
                diagnostics.agentMonitor().successfulExecutions().get(0).agenticScope();
        List<AgentInvocation> invocations = diagnostics
                .agentMonitor()
                .successfulExecutions()
                .get(0)
                .topLevelInvocations()
                .nestedInvocations();
        AgentInvocation decision = invocations.stream()
                .filter(invocation -> invocation.agent().type() == NephrologyReferral.class)
                .findFirst()
                .orElseThrow();

        assertThat(decision.decisionResponse()).isPresent();
        assertThat(decision.totalTokenCount()).isPositive();
        assertThat(scope.readState("nephrologistNeeded")).isEqualTo(true);
        assertThat(scope.readState("nephrologyAssessment", "")).isNotBlank();
        System.out.println("Nephrology assessment: " + scope.readState("nephrologyAssessment"));
    }
}
