package dev.langchain4j.micrometer.conventions;

import java.util.Arrays;

public enum AiProvider {
    // Keep alphabetical sorted.
    /**
     * AI system provided by Anthropic.
     */
    ANTHROPIC("anthropic"),

    /**
     * AI inference system provided by Azure.
     */
    AZURE_AI_INFERENCE("azure_ai_inference"),

    /**
     * AI system provided by Azure.
     */
    AZUREOPENAI("azure-openai"),

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
    IBMWATSONXAI("ibm-watsonx-ai"),

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

    AiProvider(String value) {
        this.value = value;
    }

    /**
     * Return the value of the provider.
     * @return the value of the provider
     */
    public String value() {
        return this.value;
    }

    public static AiProvider fromClass(Class<?> clazz) {
        if (clazz == null) {
            return LANGCHAIN4J;
        }

        String className = clazz.getSimpleName();
        // Remove common suffixes if present
        String baseClassName = className
                .replace("ChatRequest", "")
                .replace("ChatResponse", "")
                .toLowerCase();

        return Arrays.stream(values())
                .filter(provider -> !provider.equals(LANGCHAIN4J) &&
                        baseClassName.contains(provider.value()))
                .findFirst()
                .orElse(LANGCHAIN4J);
    }
}
