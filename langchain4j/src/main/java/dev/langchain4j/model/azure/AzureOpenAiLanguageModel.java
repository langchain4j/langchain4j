package dev.langchain4j.model.azure;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

/**
 * Represents a connection to the Azure OpenAI LLM with a completion interface.
 * However, it's recommended to use {@link OpenAiChatModel} instead,
 * as it offers more advanced features like function calling, multi-turn conversations, etc.
 * <p>
 * There are two primary authentication methods to access Azure OpenAI:
 * <p>
 * 1. API Key Authentication: For this type of authentication, HTTP requests must include the
 * API Key in the "api-key" HTTP header.
 * <p>
 * 2. Azure Active Directory Authentication: For this type of authentication, HTTP requests must include the
 * authentication/access token in the "Authorization" HTTP header.
 * <p>
 * <a href="https://learn.microsoft.com/en-us/azure/ai-services/openai/reference">More information</a>
 * <p>
 * Please note, that currently, only API Key authentication is supported by this class,
 * second authentication option will be supported later.
 */
public class AzureOpenAiLanguageModel implements LanguageModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final Double temperature;
    private final Integer maxRetries;
    private final Tokenizer tokenizer;

    @Builder
    public AzureOpenAiLanguageModel(String baseUrl,
                                    String apiVersion,
                                    String apiKey,
                                    Tokenizer tokenizer,
                                    Double temperature,
                                    Duration timeout,
                                    Integer maxRetries,
                                    Proxy proxy,
                                    Boolean logRequests,
                                    Boolean logResponses) {

        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(15) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        this.client = OpenAiClient.builder()
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .azureApiKey(apiKey)
                .apiVersion(apiVersion)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.temperature = temperature;
        this.maxRetries = maxRetries;
        this.tokenizer = tokenizer;
    }

    @Override
    public String process(String text) {

        CompletionRequest request = CompletionRequest.builder()
                .prompt(text)
                .temperature(temperature)
                .build();

        CompletionResponse response = withRetry(() -> client.completion(request).execute(), maxRetries);

        return response.text();
    }

    @Override
    public int estimateTokenCount(String prompt) {
        return tokenizer.estimateTokenCountInText(prompt);
    }

}
