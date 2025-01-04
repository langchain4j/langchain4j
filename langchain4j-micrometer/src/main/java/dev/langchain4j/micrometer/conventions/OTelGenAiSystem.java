package dev.langchain4j.micrometer.conventions;

import java.util.Arrays;

/**
 * Metric attribute for system to describe the family of GenAI models that is used for the operation.
 * The values are in line with the OpenTelemetry Semantic Conventions for Generative AI Metrics.
 */
public enum OTelGenAiSystem {
    /**
     * AI system provided by Anthropic.
     */
    ANTHROPIC("anthropic"),

    /**
     * AI inference system provided by Azure.
     */
    AZURE_AI_INFERENCE("az.ai.inference"),

    /**
     * AI system provided by Azure.
     */
    AZUREOPENAI("azure_openai"),

    /**
     * AI system provided by AWS Bedrock.
     */
    BEDROCK("aws.bedrock"),

    /**
     * AI system provided by Cohere.
     */
    COHERE("cohere"),

    /**
     * AI system provided by IBM Watsonx AI.
     */
    IBMWATSONXAI("ibm.watsonx.ai"),

    /**
     * AI system provided by Langchain. Default if no other provider is detected.
     */
    LANGCHAIN4J("langchain4j"),

    /**
     * AI system provided by Mistral.
     */
    MISTRALAI("mistral_ai"),

    /**
     * AI system provided by Ollama.
     */
    OLLAMA("ollama"),

    /**
     * AI system provided by OpenAI.
     */
    OPENAI("openai"),

    /**
     * AI system provided by Vertex AI.
     */
    VERTEXAI("vertex_ai");

    private final String value;

    OTelGenAiSystem(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }

    /**
     * Returns the OTelGenAiSystem enum value based on the class name of the implemented ChatRequest or ChatResponse classes.
     * These classes are expected to be named in the format: [SystemName]ChatRequest or [SystemName]ChatResponse.
     *
     * @param clazz The class to determine the system for.
     *
     * @return OTelGenAiSystem The system name based on the class name, or langchain4j if no other system is detected.
     */
    public static OTelGenAiSystem fromClass(Class<?> clazz) {
        if (clazz == null) {
            return LANGCHAIN4J;
        }

        String className = clazz.getSimpleName();
        // Remove suffixes of interfaces if present to derive the implemented system name
        String baseClassName =
                className.replace("ChatRequest", "").replace("ChatResponse", "").toLowerCase();

        return Arrays.stream(values())
                .filter(provider -> !provider.equals(LANGCHAIN4J) && baseClassName.contains(provider.value()))
                .findFirst()
                .orElse(LANGCHAIN4J);
    }
}
