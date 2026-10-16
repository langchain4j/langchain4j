package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.ModelProvider.OPEN_AI;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceResponse;
import dev.langchain4j.model.openai.spi.OpenAiVoiceModelBuilderFactory;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Represents the OpenAI custom voices API, used to create a custom voice from a text description. <br/>
 * You can find a description of the parameters
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voices">here</a>.
 *
 * @since 1.23.0
 */
@Experimental
public class OpenAiVoiceModel {

    private static final int MAX_NAME_LENGTH = 256;
    private static final int MAX_PROMPT_LENGTH = 2000;
    private static final int MAX_SCRIPT_HINT_LENGTH = 2000;

    private final OpenAiClient client;
    private final int maxRetries;

    public OpenAiVoiceModel(Builder builder) {
        this.client = OpenAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_OPENAI_URL))
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .projectId(builder.projectId)
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(15)))
                .readTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .userAgent(DEFAULT_USER_AGENT)
                .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    public OpenAiCreateVoiceResponse createVoice(OpenAiCreateVoiceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (isNullOrBlank(request.name())) {
            throw new IllegalArgumentException("Name is required");
        }
        if (request.name().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Name exceeds the maximum length of " + MAX_NAME_LENGTH + " characters");
        }
        if (isNullOrBlank(request.prompt())) {
            throw new IllegalArgumentException("Prompt is required");
        }
        if (request.prompt().length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException(
                    "Prompt exceeds the maximum length of " + MAX_PROMPT_LENGTH + " characters");
        }
        if (request.scriptHint() != null && request.scriptHint().isEmpty()) {
            throw new IllegalArgumentException("Script hint must not be empty");
        }
        if (request.scriptHint() != null && request.scriptHint().length() > MAX_SCRIPT_HINT_LENGTH) {
            throw new IllegalArgumentException(
                    "Script hint exceeds the maximum length of " + MAX_SCRIPT_HINT_LENGTH + " characters");
        }

        return withRetryMappingExceptions(() -> client.createVoice(request).execute(), maxRetries);
    }

    public ModelProvider provider() {
        return OPEN_AI;
    }

    public static Builder builder() {
        for (OpenAiVoiceModelBuilderFactory factory : loadFactories(OpenAiVoiceModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;

        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        public Builder() {
            // This is public so it can be extended
        }

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public OpenAiVoiceModel build() {
            return new OpenAiVoiceModel(this);
        }
    }
}
