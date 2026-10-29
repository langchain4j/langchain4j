package dev.langchain4j.agentic.patterns.cascade.support;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.Models.enhancedModel;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.ACCEPTANCE_CRITERION;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.ACCEPTANCE_THRESHOLD;
import static dev.langchain4j.agentic.patterns.cascade.support.SupportAgents.decisionModel;

import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.cascade.ConfidenceCascadePlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;

public class DeclarativeSupportAgents {

    public interface DeclarativeFaqAgent extends SupportAgents.FaqAgent {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }

    public interface DeclarativeExpertAgent extends SupportAgents.ExpertAgent {

        @ChatModelSupplier
        static ChatModel chatModel() {
            return enhancedModel();
        }
    }

    public interface DeclarativeSupportCascade {

        @PlannerAgent(
                outputKey = "answer",
                subAgents = {DeclarativeFaqAgent.class, DeclarativeExpertAgent.class})
        ResultWithAgenticScope<String> ask(@V("request") String request);

        @PlannerSupplier
        static Planner planner() {
            return new ConfidenceCascadePlanner(decisionModel(), ACCEPTANCE_THRESHOLD, ACCEPTANCE_CRITERION);
        }
    }
}
