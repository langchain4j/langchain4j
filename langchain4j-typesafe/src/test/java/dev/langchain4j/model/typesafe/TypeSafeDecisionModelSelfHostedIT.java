package dev.langchain4j.model.typesafe;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.common.AbstractDecisionModelIT;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs the common decision model tests against a self-hosted server implementing the System One API
 * (for example Laya, Kev or jitLLM), whose base URL and model name are given by the {@code SYSTEM_ONE_BASE_URL} and
 * {@code SYSTEM_ONE_MODEL_NAME} environment variables. Such servers usually need no API key.
 */
@EnabledIfEnvironmentVariable(named = "SYSTEM_ONE_BASE_URL", matches = ".+")
class TypeSafeDecisionModelSelfHostedIT extends AbstractDecisionModelIT {

    static TypeSafeDecisionModel.TypeSafeDecisionModelBuilder selfHosted() {
        return TypeSafeDecisionModel.builder()
                .baseUrl(System.getenv("SYSTEM_ONE_BASE_URL"))
                .modelName(System.getenv("SYSTEM_ONE_MODEL_NAME"));
    }

    @Override
    protected DecisionModel model() {
        return selfHosted().logRequests(true).logResponses(true).build();
    }

    @Override
    protected DecisionModel modelWithListener(DecisionModelListener listener) {
        return selfHosted().listeners(listener).build();
    }
}
