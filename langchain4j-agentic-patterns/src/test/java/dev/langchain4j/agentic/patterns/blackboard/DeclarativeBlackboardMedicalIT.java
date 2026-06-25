package dev.langchain4j.agentic.patterns.blackboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.blackboard.DeclarativeBlackboardMedicalAgents.DeclarativeMedicalDiagnostics;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class DeclarativeBlackboardMedicalIT {

    @Test
    void declarative_medical_diagnosis_with_blackboard() {
        DeclarativeMedicalDiagnostics diagnostics =
                AgenticServices.createAgenticSystem(DeclarativeMedicalDiagnostics.class);

        String result = diagnostics.diagnose(
                "Patient reports persistent headaches, dizziness, and blurred vision for the past two weeks. "
                        + "Blood pressure measured at 180/110 mmHg.",
                "CBC: normal. BMP: elevated creatinine (2.1 mg/dL), elevated BUN (35 mg/dL). "
                        + "Urinalysis: proteinuria detected.",
                "Lisinopril 10mg daily, Metformin 500mg twice daily");

        assertThat(result).isNotBlank();
        System.out.println("Diagnosis: " + result);

//        HtmlReportGenerator.generateReport(diagnostics.agentMonitor(),
//                Path.of("src", "test", "resources", "blackboard-medical-declarative-report.html"));
    }
}
