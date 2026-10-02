package dev.langchain4j.agentic.patterns.decisionrouter.experts;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.decisionrouter.experts.ExpertAgents.decisionModel;

import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;
import java.util.Map;

public class DeclarativeExpertAgents {

    public interface DeclarativeMedicalExpert extends ExpertAgents.MedicalExpert {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeLegalExpert extends ExpertAgents.LegalExpert {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeTechnicalExpert extends ExpertAgents.TechnicalExpert {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeExpertRouter {

        @PlannerAgent(
                outputKey = "response",
                subAgents = {
                    DeclarativeMedicalExpert.class,
                    DeclarativeLegalExpert.class,
                    DeclarativeTechnicalExpert.class
                })
        ResultWithAgenticScope<String> ask(@V("request") String request);

        @PlannerSupplier
        static Planner planner() {
            return new DecisionRouterPlanner(decisionModel());
        }
    }

    public interface DeclarativeMultiExpertRouter {

        @PlannerAgent(
                outputKey = "responses",
                subAgents = {
                    DeclarativeMedicalExpert.class,
                    DeclarativeLegalExpert.class,
                    DeclarativeTechnicalExpert.class
                })
        ResultWithAgenticScope<Map<String, String>> ask(@V("request") String request);

        @PlannerSupplier
        static Planner planner() {
            return new DecisionRouterPlanner(decisionModel(), 0.5);
        }
    }
}
