package dev.langchain4j.model.localai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.localai.spi.LocalAiLanguageModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.finishReasonFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * See <a href="https://localai.io/features/text-generation/">LocalAI documentation</a> for more details.
 */
public class LocalAiLanguageModel implements LanguageModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Integer maxRetries;

    @Builder
    public LocalAiLanguageModel(String baseUrl,
                                String modelName,
                                Double temperature,
                                Double topP,
                                Integer maxTokens,
                                Duration timeout,
                                Integer maxRetries,
                                Boolean logRequests,
                                Boolean logResponses) {

        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(60) : timeout;
        maxRetries = maxRetries == null ? 3 : maxRetries;

        this.client = OpenAiClient.builder()
                .openAiApiKey("ignored")
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.maxRetries = maxRetries;
    }

    @Override
    public Response<String> generate(String prompt) {

        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .build();

        CompletionResponse response = withRetry(() -> client.completion(request).execute(), maxRetries);

        return Response.from(
                response.text(),
                null,
                finishReasonFrom(response.choices().get(0).finishReason())
        );
    }

    public static LocalAiLanguageModelBuilder builder() {
        for (LocalAiLanguageModelBuilderFactory factory : loadFactories(LocalAiLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new LocalAiLanguageModelBuilder();
    }

    public static class LocalAiLanguageModelBuilder {
        public LocalAiLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
