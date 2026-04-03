package dev.langchain4j.model;

public enum ModelProvider {
    ANTHROPIC,
    AMAZON_BEDROCK,
    GITHUB_MODELS,
    // Azure OpenAI is deprecated in favor of Microsoft Foundry
    @Deprecated
    AZURE_OPEN_AI,
    GOOGLE_AI_GEMINI,
    GOOGLE_VERTEX_AI_GEMINI,
    GOOGLE_VERTEX_AI_ANTHROPIC,
    MICROSOFT_FOUNDRY,
    MISTRAL_AI,
    OLLAMA,
    OPEN_AI,
    WATSONX,
    OTHER
}
