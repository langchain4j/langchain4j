package dev.langchain4j.agentic.patterns.blackboard;

import static dev.langchain4j.agentic.patterns.Models.baseModel;

import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;

public class DeclarativeBlackboardMedicalAgents {

    public interface DeclarativeSymptomExtractor extends BlackboardMedicalAgents.SymptomExtractor {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeLabResultAnalyzer extends BlackboardMedicalAgents.LabResultAnalyzer {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeDrugInteractionChecker extends BlackboardMedicalAgents.DrugInteractionChecker {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeDiagnosisAgent extends BlackboardMedicalAgents.DiagnosisAgent {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeMedicalDiagnostics extends MonitoredAgent {

        @PlannerAgent(
                name = "medicalBlackboard",
                outputKey = "diagnosis",
                subAgents = {DeclarativeSymptomExtractor.class, DeclarativeLabResultAnalyzer.class,
                        DeclarativeDrugInteractionChecker.class, DeclarativeDiagnosisAgent.class})
        String diagnose(@V("patientInput") String patientInput,
                        @V("labResults") String labResults,
                        @V("medications") String medications);

        @PlannerSupplier
        static Planner planner() {
            return new BlackboardPlanner();
        }
    }
}
