package dev.langchain4j.agentic.patterns.decisionscheduler.medical;

import static dev.langchain4j.agentic.patterns.DecisionModels.decisionModel;
import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.DiagnosisAgent;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.DrugInteractionChecker;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.LabResultAnalyzer;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.MedicalDiagnostics;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.SymptomExtractor;
import dev.langchain4j.agentic.patterns.decisionscheduler.DecisionSchedulerPlanner;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The medical diagnostics of the blackboard pattern, with the same agents, where a decision model chooses which of
 * the agents that can run is the most useful, instead of a hand-written conflict resolution strategy.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionSchedulerMedicalIT {

    private static final String GOAL = "A diagnosis based on the symptoms and on the lab analysis is available";

    @Test
    void medical_diagnosis_with_the_decision_scheduler() {
        AgentMonitor monitor = new AgentMonitor();

        SymptomExtractor symptomExtractor = AgenticServices.agentBuilder(SymptomExtractor.class)
                .chatModel(baseModel())
                .build();
        LabResultAnalyzer labAnalyzer = AgenticServices.agentBuilder(LabResultAnalyzer.class)
                .chatModel(baseModel())
                .build();
        DrugInteractionChecker drugInteraction = AgenticServices.agentBuilder(DrugInteractionChecker.class)
                .chatModel(baseModel())
                .build();
        DiagnosisAgent diagnosis = AgenticServices.agentBuilder(DiagnosisAgent.class)
                .chatModel(baseModel())
                .build();

        MedicalDiagnostics diagnostics = AgenticServices.plannerBuilder(MedicalDiagnostics.class)
                .subAgents(symptomExtractor, labAnalyzer, drugInteraction, diagnosis)
                .planner(() -> DecisionSchedulerPlanner.builder()
                        .decisionModel(decisionModel())
                        .goal(GOAL)
                        .goalThreshold(0.5)
                        .activationThreshold(0.5)
                        .build())
                .outputKey("diagnosis")
                .listener(monitor)
                .build();

        String result = diagnostics.diagnose(
                "Patient reports persistent headaches, dizziness, and blurred vision for the past two weeks. "
                        + "Blood pressure measured at 180/110 mmHg.",
                "CBC: normal. BMP: elevated creatinine (2.1 mg/dL), elevated BUN (35 mg/dL). "
                        + "Urinalysis: proteinuria detected.",
                "Lisinopril 10mg daily, Metformin 500mg twice daily");

        List<String> path =
                diagnostics.agentMonitor().successfulExecutions().get(0).topLevelInvocations().nestedInvocations()
                        .stream()
                        .map(invocation -> invocation.agent().name())
                        .toList();
        System.out.println("Medical path: " + path);
        System.out.println("Diagnosis: " + result);

        assertThat(result).isNotBlank();
        // the diagnosis needs the symptoms and the lab analysis, and reaches the goal
        assertThat(path).endsWith("diagnose").containsOnlyOnce("diagnose");
        assertThat(path).contains("extractSymptoms", "analyzeLabResults");
        // the tokens of the decision calls of the scheduler itself, without the ones of its subagents
        assertThat(monitor.successfulExecutions().get(0).topLevelInvocations().totalTokenCount())
                .isPositive();
//        HtmlReportGenerator.generateReport(monitor,
//                Path.of("src", "test", "resources", "medical-decision-scheduler.html"));
    }
}
