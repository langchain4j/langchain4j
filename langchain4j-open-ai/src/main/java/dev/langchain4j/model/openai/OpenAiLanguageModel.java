package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionChoice;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.TokenCountEstimator;
import dev.langchain4j.model.openai.spi.OpenAiLanguageModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.*;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO_INSTRUCT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * Represents an OpenAI language model with a completion interface, such as gpt-3.5-turbo-instruct.
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
    public OpenAiLanguageModel(String baseUrl,
                               String apiKey,
                               String organizationId,
                               String modelName,
                               Double temperature,
                               Duration timeout,
                               Integer maxRetries,
                               Proxy proxy,
                               Boolean logRequests,
                               Boolean logResponses,
                               Tokenizer tokenizer) {

        timeout = getOrDefault(timeout, ofSeconds(60));

        this.client = OpenAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, OPENAI_URL))
                .openAiApiKey(apiKey)
                .organizationId(organizationId)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .userAgent(DEFAULT_USER_AGENT)
                .build();
        this.modelName = getOrDefault(modelName, GPT_3_5_TURBO_INSTRUCT);
        this.temperature = getOrDefault(temperature, 0.7);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.tokenizer = getOrDefault(tokenizer, () -> new OpenAiTokenizer(this.modelName));
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<String> generate(String prompt) {

        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .temperature(temperature)
                .build();

        CompletionResponse response = withRetry(() -> client.completion(request).execute(), maxRetries);

        CompletionChoice completionChoice = response.choices().get(0);
        return Response.from(
                completionChoice.text(),
                tokenUsageFrom(response.usage()),
                finishReasonFrom(completionChoice.finishReason())
        );
    }

    @Override
    public int estimateTokenCount(String prompt) {
        return tokenizer.estimateTokenCountInText(prompt);
    }

    public static OpenAiLanguageModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static OpenAiLanguageModelBuilder builder() {
        for (OpenAiLanguageModelBuilderFactory factory : loadFactories(OpenAiLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiLanguageModelBuilder();
    }

    public static class OpenAiLanguageModelBuilder {

        public OpenAiLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public OpenAiLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiLanguageModelBuilder modelName(OpenAiLanguageModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }
}
