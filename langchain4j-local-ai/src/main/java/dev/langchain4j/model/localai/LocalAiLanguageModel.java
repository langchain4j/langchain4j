package dev.langchain4j.model.localai;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.localai.spi.LocalAiLanguageModelBuilderFactory;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.openai.internal.completion.CompletionResponse;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;

import java.time.Duration;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.finishReasonFrom;
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

    @Deprecated(forRemoval = true, since = "1.5.0")
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
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.maxRetries = maxRetries;
    }

    public LocalAiLanguageModel(LocalAiLanguageModelBuilder builder) {
        this.client = OpenAiClient.builder()
                .baseUrl(ensureNotBlank(builder.baseUrl, "baseUrl"))
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .readTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logger(builder.logger)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.temperature = getOrDefault(builder.temperature, 0.7);
        this.topP = builder.topP;
        this.maxTokens = builder.maxTokens;
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
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

        CompletionResponse response = withRetryMappingExceptions(() -> client.completion(request).execute(), maxRetries);

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
        private String baseUrl;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        public LocalAiLanguageModelBuilder() {
            // This is public so it can be extended
        }

        public LocalAiLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public LocalAiLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public LocalAiLanguageModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public LocalAiLanguageModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public LocalAiLanguageModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public LocalAiLanguageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public LocalAiLanguageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public LocalAiLanguageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public LocalAiLanguageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public LocalAiLanguageModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public LocalAiLanguageModel build() {
            return new LocalAiLanguageModel(this);
        }

        public String toString() {
            return "LocalAiLanguageModel.LocalAiLanguageModelBuilder(baseUrl=" + this.baseUrl + ", modelName=" + this.modelName + ", temperature=" + this.temperature + ", topP=" + this.topP + ", maxTokens=" + this.maxTokens + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
