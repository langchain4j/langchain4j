package dev.langchain4j.agentic.decision;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.openai.OpenAiDecisionModel;
import dev.langchain4j.model.openai.OpenAiDecisionModelName;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;

/**
 * The decision models of the integration tests: TypeSafe {@code jev-1.13.0} for the text scenarios, the provider
 * and version of every other decision model IT of the repository, and the OpenAI decision model for the images.
 */
public class DecisionModels {

    public static DecisionModel typeSafe() {
        return TypeSafeDecisionModel.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .modelName("jev-1.13.0")
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    public static DecisionModel openAi() {
        return OpenAiDecisionModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(OpenAiDecisionModelName.GPT_6_LUNA)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
