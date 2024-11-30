package dev.langchain4j.micrometer.conventions;

// Copied from AiProvider.java in spring-ai-core
public enum AiProvider {
    // Please, keep the alphabetical sorting.
    /**
     * AI system provided by Anthropic.
     */
    ANTHROPIC("anthropic"),

    /**
     * AI system provided by Azure.
     */
    AZURE_OPENAI("azure-openai"),

    /**
     * AI system provided by Bedrock Converse.
     */
    BEDROCK_CONVERSE("bedrock_converse"),

    /**
     * AI system provided by Mistral.
     */
    MISTRAL_AI("mistral_ai"),

    /**
     * AI system provided by Ollama.
     */
    OLLAMA("ollama"),

    /**
     * AI system provided by OpenAI.
     */
    OPENAI("openai"),

    /**
     * AI system provided by Spring AI.
     */
    SPRING_AI("spring_ai");

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
}
