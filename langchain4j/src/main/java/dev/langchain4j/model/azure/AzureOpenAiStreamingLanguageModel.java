package dev.langchain4j.model.azure;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

/**
 * Represents a connection to the OpenAI LLM, hosted on Azure (like text-davinci-003).
 * The LLM's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * However, it's recommended to use {@link OpenAiStreamingChatModel} instead,
 * as it offers more advanced features like function calling, multi-turn conversations, etc.
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
public class AzureOpenAiStreamingLanguageModel implements StreamingLanguageModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final Double temperature;
    private final Tokenizer tokenizer;

    @Builder
    public AzureOpenAiStreamingLanguageModel(String baseUrl,
                                             String apiVersion,
                                             String apiKey,
                                             Tokenizer tokenizer,
                                             Double temperature,
                                             Duration timeout,
                                             Proxy proxy,
                                             Boolean logRequests,
                                             Boolean logResponses) {

        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(15) : timeout;

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
        this.tokenizer = tokenizer;
    }

    @Override
    public void process(String text, StreamingResponseHandler handler) {

        CompletionRequest request = CompletionRequest.builder()
                .prompt(text)
                .temperature(temperature)
                .build();

        client.completion(request)
                .onPartialResponse(partialResponse -> {
                    String partialResponseText = partialResponse.text();
                    if (partialResponseText != null) {
                        handler.onNext(partialResponseText);
                    }
                })
                .onComplete(handler::onComplete)
                .onError(handler::onError)
                .execute();
    }

    @Override
    public int estimateTokenCount(String prompt) {
        return tokenizer.estimateTokenCountInText(prompt);
    }

}
