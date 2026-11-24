package dev.langchain4j.decision.it;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;

class DecisionModels {

    static final String MODEL_NAME = "jev-1.13.0";

    static DecisionModel typeSafe() {
        return TypeSafeDecisionModel.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .modelName(MODEL_NAME)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
