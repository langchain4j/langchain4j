package dev.langchain4j.agentic.patterns;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.ollama.OllamaDecisionModel;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;

import java.time.Duration;

public class DecisionModels {

    private enum MODEL_PROVIDER {
        TYPESAFE,
        OLLAMA
    }

    private static final MODEL_PROVIDER modelProvider = MODEL_PROVIDER.TYPESAFE;

    private static final String OLLAMA_DEFAULT_URL = "http://127.0.0.1:11434";
    private static final String OLLAMA_ENV_URL = System.getenv("OLLAMA_BASE_URL");

    private static final String OLLAMA_BASE_URL = OLLAMA_ENV_URL != null ? OLLAMA_ENV_URL : OLLAMA_DEFAULT_URL;

    // only the model of the selected provider is created, since the TypeSafe one cannot be built without its API key
    public static DecisionModel decisionModel() {
        return switch (modelProvider) {
            case TYPESAFE -> typeSafeDecisionModel();
            case OLLAMA -> ollamaDecisionModel();
        };
    }

    private static DecisionModel typeSafeDecisionModel() {
        return TypeSafeDecisionModel.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .modelName("jev-1.13.0")
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    private static DecisionModel ollamaDecisionModel() {
        return OllamaDecisionModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName("tev1:0.8b")
                .timeout(Duration.ofMinutes(10))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
