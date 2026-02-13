package dev.langchain4j.micrometer.metrics.conventions;

import dev.langchain4j.model.ModelProvider;

/**
 * Maps {@link ModelProvider} values to OpenTelemetry Semantic Conventions (v1.39.0)
 * well-known values for the {@code gen_ai.provider.name} attribute.
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/">OTel GenAI Spans</a>
 */
public enum OTelGenAiProviderName {

    ANTHROPIC(ModelProvider.ANTHROPIC, "anthropic"),
    AMAZON_BEDROCK(ModelProvider.AMAZON_BEDROCK, "aws.bedrock"),
    // Azure OpenAI is deprecated in favor of Microsoft Foundry
    @Deprecated
    AZURE_OPEN_AI(ModelProvider.AZURE_OPEN_AI, "azure.ai.openai"),
    GITHUB_MODELS(ModelProvider.GITHUB_MODELS, "github"),
    GOOGLE_AI_GEMINI(ModelProvider.GOOGLE_AI_GEMINI, "gcp.gemini"),
    GOOGLE_VERTEX_AI_GEMINI(ModelProvider.GOOGLE_VERTEX_AI_GEMINI, "gcp.vertex_ai"),
    GOOGLE_VERTEX_AI_ANTHROPIC(ModelProvider.GOOGLE_VERTEX_AI_ANTHROPIC, "gcp.vertex_ai"),
    MICROSOFT_FOUNDRY(ModelProvider.MICROSOFT_FOUNDRY, "azure.ai.inference"),
    MISTRAL_AI(ModelProvider.MISTRAL_AI, "mistral_ai"),
    OLLAMA(ModelProvider.OLLAMA, "ollama"),
    OPEN_AI(ModelProvider.OPEN_AI, "openai"),
    WATSONX(ModelProvider.WATSONX, "ibm.watsonx.ai"),
    OTHER(ModelProvider.OTHER, "other");

    private final ModelProvider modelProvider;
    private final String value;

    OTelGenAiProviderName(ModelProvider modelProvider, String value) {
        this.modelProvider = modelProvider;
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * Returns the OTel-conformant provider name for the given {@link ModelProvider},
     * or {@code "unknown"} if the provider is {@code null} or not mapped.
     *
     * @param modelProvider the {@link ModelProvider} to look up
     * @return the OTel well-known provider name
     */
    public static String fromModelProvider(ModelProvider modelProvider) {
        if (modelProvider == null) {
            return "unknown";
        }
        for (OTelGenAiProviderName entry : values()) {
            if (entry.modelProvider == modelProvider) {
                return entry.value;
            }
        }
        return "unknown";
    }
}
