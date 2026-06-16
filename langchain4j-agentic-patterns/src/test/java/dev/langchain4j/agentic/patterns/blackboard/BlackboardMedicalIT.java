package dev.langchain4j.agentic.patterns.blackboard;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.DiagnosisAgent;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.DrugInteractionChecker;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.LabResultAnalyzer;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.MedicalDiagnostics;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardMedicalAgents.SymptomExtractor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class BlackboardMedicalIT {

    @Test
    void medical_diagnosis_with_blackboard_default_ordering() {
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
        System.out.println("Diagnosis: " + result);

//        HtmlReportGenerator.generateReport(diagnostics.agentMonitor(),
//                Path.of("src", "test", "resources", "blackboard-medical-report.html"));
    }
}
