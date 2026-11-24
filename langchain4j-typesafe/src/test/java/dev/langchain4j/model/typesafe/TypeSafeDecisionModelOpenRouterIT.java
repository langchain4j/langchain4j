package dev.langchain4j.model.typesafe;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.common.AbstractDecisionModelIT;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs the common decision model tests against OpenRouter, which serves the System One API.
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class TypeSafeDecisionModelOpenRouterIT extends AbstractDecisionModelIT {

    static TypeSafeDecisionModel.TypeSafeDecisionModelBuilder openRouter() {
        return TypeSafeDecisionModel.builder()
                .baseUrl("https://openrouter.ai/api/")
                .apiKey(System.getenv("OPENROUTER_API_KEY"))
                .modelName("typesafe/jev-1.13");
    }

    @Override
    protected DecisionModel model() {
        return openRouter().logRequests(true).logResponses(true).build();
    }

    @Override
    protected boolean reportsProbabilities() {
        return true;
    }

    @Override
    protected boolean reportsTokenUsage() {
        return true;
    }

    @Override
    protected DecisionModel modelWithListener(DecisionModelListener listener) {
        return openRouter().listeners(listener).build();
    }

    @Override
    protected String requestModelName() {
        return "typesafe/jev-1.13";
    }
}
