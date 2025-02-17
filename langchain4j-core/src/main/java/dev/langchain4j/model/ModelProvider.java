package dev.langchain4j.model;

public enum ModelProvider { // TODO client id?

    // TODO also add source?

    // TODO naming scheme: company_product?

    ANTHROPIC, // anthropic
    AMAZON_BEDROCK, // aws.bedrock
    AZURE_AI_INFERENCE, // az.ai.inference
    AZURE_OPEN_AI, // az.ai.openai
    GOOGLE_GEMINI, // gemini
    GOOGLE_VERTEX_AI, // vertex_ai
    MISTRAL_AI, // mistral_ai
    OLLAMA,
    OPEN_AI, // openai
}
