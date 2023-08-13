package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_DAVINCI_003;
import static java.time.Duration.ofSeconds;

/**
 * Represents a connection to the OpenAI LLM with a completion interface, such as text-davinci-003.
 * However, it's recommended to use {@link OpenAiChatModel} instead,
 * as it offers more advanced features like function calling, multi-turn conversations, etc.
 */
public class OpenAiLanguageModel implements LanguageModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer maxRetries;
    private final Tokenizer tokenizer;

    @Builder
    public OpenAiLanguageModel(String apiKey,
                               String modelName,
                               Double temperature,
                               Duration timeout,
                               Integer maxRetries,
                               Proxy proxy,
                               Boolean logRequests,
                               Boolean logResponses) {

        modelName = modelName == null ? TEXT_DAVINCI_003 : modelName;
        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(15) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        this.client = OpenAiClient.builder()
                .apiKey(apiKey)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxRetries = maxRetries;
        this.tokenizer = new OpenAiTokenizer(this.modelName);
    }

    @Override
    public String process(String text) {

        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
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

    public static OpenAiLanguageModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
