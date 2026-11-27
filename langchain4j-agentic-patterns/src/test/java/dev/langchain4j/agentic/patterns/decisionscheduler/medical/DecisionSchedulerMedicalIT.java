package dev.langchain4j.agentic.patterns.decisionscheduler.medical;

import static dev.langchain4j.agentic.patterns.DecisionModels.decisionModel;
import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.observability.PlannerModelResponse;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.DiagnosisAgent;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.DrugInteractionChecker;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.LabResultAnalyzer;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.MedicalDiagnostics;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.SymptomExtractor;
import dev.langchain4j.agentic.patterns.decisionscheduler.DecisionSchedulerPlanner;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final double GOAL_THRESHOLD = 0.5;
    private static final double ACTIVATION_THRESHOLD = 0.5;

    @Test
    void medical_diagnosis_with_the_decision_scheduler() {
        AgentMonitor monitor = new AgentMonitor();
        // prints the answers of each decision of the scheduler: the usefulness of every agent that can run and, once
        // there is a diagnosis, the probability that the goal has been reached
        List<DecisionResponse> decisions = new ArrayList<>();
        AgentListener decisionPrinter = new AgentListener() {
            @Override
            public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
                DecisionResponse decision = plannerModelResponse.decisionResponse();
                // recorded before reading it, since an exception thrown by a listener is only logged
                decisions.add(decision);
                System.out.println("Decision:");
                decision.answers().forEach((question, answer) -> System.out.println("  " + question + ": " + answer));
            }
        };

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
                        .goalThreshold(GOAL_THRESHOLD)
                        .activationThreshold(ACTIVATION_THRESHOLD)
                        .build())
                .outputKey("diagnosis")
                .listener(monitor)
                .listener(decisionPrinter)
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
        // one decision before each invocation, and a last one that stopped the scheduler, unless no agent was left
        assertThat(decisions).doesNotContainNull().hasSizeBetween(path.size(), path.size() + 1);
        for (int i = 0; i < path.size(); i++) {
            assertThat(agentToInvoke(decisions.get(i))).isEqualTo(path.get(i));
        }
        if (decisions.size() > path.size()) {
            assertThat(agentToInvoke(decisions.get(path.size()))).isNull();
        }
//        HtmlReportGenerator.generateReport(monitor,
//                Path.of("src", "test", "resources", "medical-decision-scheduler.html"));
    }

    /**
     * The agent that the scheduler invokes after this decision, or {@code null} if it stops there: it stops when the
     * probability that the goal has been reached is at least the goal threshold, and otherwise invokes the most useful
     * agent, the first one asked winning a tie, when its mean usefulness level, scaled between 0 and 1, is at least
     * the activation threshold.
     */
    private static String agentToInvoke(DecisionResponse decision) {
        if (decision.answers().get(DecisionSchedulerPlanner.GOAL_QUESTION_NAME) instanceof YesNoAnswer goal
                && goal.probability() >= GOAL_THRESHOLD) {
            return null;
        }
        String mostUseful = null;
        double highestScore = -1;
        for (Map.Entry<String, DecisionAnswer> entry : decision.answers().entrySet()) {
            if (entry.getValue() instanceof ScaleAnswer usefulness) {
                double score = usefulness.mean() / (DecisionSchedulerPlanner.DEFAULT_USEFULNESS_LEVELS.size() - 1);
                if (score > highestScore) {
                    mostUseful = entry.getKey();
                    highestScore = score;
                }
            }
        }
        return highestScore >= ACTIVATION_THRESHOLD ? mostUseful : null;
    }
}
