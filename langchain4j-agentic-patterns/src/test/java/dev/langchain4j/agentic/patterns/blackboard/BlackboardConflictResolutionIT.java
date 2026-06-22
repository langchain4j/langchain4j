package dev.langchain4j.agentic.patterns.blackboard;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.blackboard.ConflictResolutionStrategy.agentOfType;
import static dev.langchain4j.agentic.patterns.blackboard.ConflictResolutionStrategy.declarationOrder;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.DiagnosisAgent;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.DrugInteractionChecker;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.LabResultAnalyzer;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.MedicalDiagnostics;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.SymptomExtractor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class BlackboardConflictResolutionIT {

    @Test
    void blackboard_with_drug_aware_conflict_resolution_and_human_review() {
        SymptomExtractor symptomExtractor = AgenticServices.agentBuilder(SymptomExtractor.class)
                .chatModel(baseModel())
                .build();

        LabResultAnalyzer labAnalyzer = AgenticServices.agentBuilder(LabResultAnalyzer.class)
                .chatModel(baseModel())
                .build();

        DrugInteractionChecker drugInteraction = AgenticServices.agentBuilder(DrugInteractionChecker.class)
                .chatModel(baseModel())
                .build();

        DiagnosisAgent diagnosisAgent = AgenticServices.agentBuilder(DiagnosisAgent.class)
                .chatModel(baseModel())
                .build();

        AtomicInteger reviewCount = new AtomicInteger(0);

        HumanInTheLoop humanReview = AgenticServices.humanInTheLoopBuilder()
                .description("Review the diagnosis and decide whether to approve or request additional analysis")
                .outputKey("symptoms")
                .inputKey(String.class, "diagnosis")
                .responseProvider(scope -> {
                    String diagnosis = scope.readState("diagnosis", "");
                    String symptoms = scope.readState("symptoms", "");
                    if (reviewCount.getAndIncrement() == 0) {
                        System.out.println("HITL: Rejected diagnosis, requesting re-analysis with additional symptoms");
                        return symptoms + ". Patient also reports blurred vision and occasional dizziness.";
                    }
                    System.out.println("HITL: Approved diagnosis");
                    scope.writeState("approvedDiagnosis", diagnosis);
                    return symptoms;
                })
                .build();

        MedicalDiagnostics diagnostics = AgenticServices.plannerBuilder(MedicalDiagnostics.class)
                .subAgents(symptomExtractor, labAnalyzer, drugInteraction, diagnosisAgent, humanReview)
                .planner(() -> new BlackboardPlanner(
                        scope -> scope.hasState("approvedDiagnosis"),
                        agentOfType(DrugInteractionChecker.class, scope -> {
                                    String symptoms = scope.readState("symptoms", "");
                                    // check if the symptoms are related to drugs or medications
                                    return symptoms.toLowerCase().contains("medication")
                                            || symptoms.toLowerCase().contains("drug")
                                            || symptoms.toLowerCase().contains("side effect");
                                })
                                .or(declarationOrder())
                        ))
                .outputKey("approvedDiagnosis")
                .build();

        String result = diagnostics.diagnose(
                "Patient complains of severe muscle pain and dark urine since starting a new medication. "
                        + "Currently taking multiple drugs including a statin and a fibrate.",
                "CK levels: 5000 U/L (elevated). LFTs: AST 120 U/L, ALT 95 U/L (elevated). "
                        + "Serum myoglobin: elevated.",
                "Atorvastatin 40mg daily, Gemfibrozil 600mg twice daily, Amlodipine 5mg daily");

        assertThat(result).isNotBlank();
        assertThat(reviewCount.get()).isEqualTo(2);
        System.out.println("Approved diagnosis: " + result);

//        HtmlReportGenerator.generateReport(diagnostics.agentMonitor(),
//                Path.of("src", "test", "resources", "blackboard-conflict-resolution-report.html"));
    }
}
