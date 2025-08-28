package dev.langchain4j.model.azure;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class AzureModelBuilders {

    // Use the lower max_tokens limit to avoid rate-limiting
    private static final int DEFAULT_MAX_TOKENS = 100;
    private static final double DEFAULT_TEMPERATURE = 0.1;
    public static final String DEFAULT_CHAT_MODEL = "gpt-4o-mini";

    private AzureModelBuilders() {}

    public static @NotNull String getAzureOpenaiKey() {
        return Objects.requireNonNull(
                System.getenv("AZURE_OPENAI_KEY"),
                "AZURE_OPENAI_KEY environment variable is required to run this test.");
    }

    public static String getAzureOpenaiEndpoint() {
        return System.getenv("AZURE_OPENAI_ENDPOINT");
    }

    public static AzureOpenAiChatModel.Builder chatModelBuilder() {
        return AzureOpenAiChatModel.builder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName(DEFAULT_CHAT_MODEL)
                .temperature(DEFAULT_TEMPERATURE)
                .maxTokens(DEFAULT_MAX_TOKENS)
                .logRequestsAndResponses(true);
    }

    public static AzureOpenAiStreamingChatModel.Builder streamingChatModelBuilder() {

        return AzureOpenAiStreamingChatModel.builder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName(DEFAULT_CHAT_MODEL)
                .temperature(DEFAULT_TEMPERATURE)
                .maxTokens(DEFAULT_MAX_TOKENS)
                .logRequestsAndResponses(true);
    }

    public static AzureOpenAiLanguageModel.Builder languageModelBuilder() {
        return AzureOpenAiLanguageModel.builder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName("gpt-35-turbo-instruct-0914")
                .temperature(DEFAULT_TEMPERATURE)
                .maxTokens(DEFAULT_MAX_TOKENS)
                .logRequestsAndResponses(false);
    }

    public static AzureOpenAiStreamingLanguageModel.Builder streamingLanguageModelBuilder() {
        return AzureOpenAiStreamingLanguageModel.builder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName("gpt-35-turbo-instruct-0914")
                .temperature(DEFAULT_TEMPERATURE)
                .maxTokens(DEFAULT_MAX_TOKENS)
                .logRequestsAndResponses(true);
    }

    public static AzureOpenAiEmbeddingModel.Builder embeddingModelBuilder() {
        return AzureOpenAiEmbeddingModel.builder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName("text-embedding-3-small")
                .logRequestsAndResponses(false); // embeddings are huge in logs
    }

    public static AzureOpenAiImageModel.Builder imageModelBuilder() {
        return AzureOpenAiImageModel.builder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName("dall-e-3")
                .size("128x128")
                .quality("low")
                .logRequestsAndResponses(false); // images are huge in logs;
    }

    public static AzureOpenAiAudioTranscriptionModel.Builder audioTranscriptionModelBuilder() {
        return AzureOpenAiAudioTranscriptionModel.builder()
                .endpoint(getAzureOpenaiEndpoint())
                .apiKey(getAzureOpenaiKey())
                .deploymentName(System.getenv("AZURE_OPENAI_AUDIO_DEPLOYMENT_NAME"))
                .logRequestsAndResponses(true);
    }
}
