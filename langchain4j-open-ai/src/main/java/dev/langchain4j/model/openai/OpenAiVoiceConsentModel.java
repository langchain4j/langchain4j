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
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiCreateVoiceConsentRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiDeleteVoiceConsentResponse;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiListVoiceConsentsRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiUpdateVoiceConsentRequest;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiVoiceConsentId;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiVoiceConsentListResponse;
import dev.langchain4j.model.openai.internal.audio.voice.OpenAiVoiceConsentResponse;
import dev.langchain4j.model.openai.spi.OpenAiVoiceConsentModelBuilderFactory;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Represents the OpenAI voice consents API.
 * You can find a description of the parameters
 * <a href="https://developers.openai.com/api/reference/resources/audio/subresources/voice_consents">here</a>.
 *
 * @since 1.23.0
 */
@Experimental
public class OpenAiVoiceConsentModel {

    /**
     * The maximum size of a consent recording accepted by the OpenAI voice consents API, in bytes (10 MB).
     */
    private static final int MAX_RECORDING_SIZE_BYTES = 10 * 1024 * 1024;

    private static final int MIN_LIST_LIMIT = 1;
    private static final int MAX_LIST_LIMIT = 100;

    private final OpenAiClient client;
    private final int maxRetries;

    public OpenAiVoiceConsentModel(Builder builder) {
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

    public OpenAiVoiceConsentResponse createVoiceConsent(OpenAiCreateVoiceConsentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (isNullOrBlank(request.name())) {
            throw new IllegalArgumentException("Name is required");
        }
        if (isNullOrBlank(request.language())) {
            throw new IllegalArgumentException("Language is required");
        }
        if (request.recording() == null) {
            throw new IllegalArgumentException("Recording is required");
        }
        if (request.recording().content().length > MAX_RECORDING_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Recording exceeds the maximum size of " + MAX_RECORDING_SIZE_BYTES + " bytes");
        }

        return withRetryMappingExceptions(
                () -> client.createVoiceConsent(request).execute(), maxRetries);
    }

    public OpenAiVoiceConsentListResponse listVoiceConsents() {
        return listVoiceConsents(OpenAiListVoiceConsentsRequest.builder().build());
    }

    public OpenAiVoiceConsentListResponse listVoiceConsents(OpenAiListVoiceConsentsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        Integer limit = request.limit();
        if (limit != null && (limit < MIN_LIST_LIMIT || limit > MAX_LIST_LIMIT)) {
            throw new IllegalArgumentException(
                    "Limit must be between " + MIN_LIST_LIMIT + " and " + MAX_LIST_LIMIT + ", but is " + limit);
        }

        return withRetryMappingExceptions(
                () -> client.listVoiceConsents(request).execute(), maxRetries);
    }

    public OpenAiVoiceConsentResponse retrieveVoiceConsent(OpenAiVoiceConsentId request) {
        validate(request);

        return withRetryMappingExceptions(
                () -> client.retrieveVoiceConsent(request).execute(), maxRetries);
    }

    public OpenAiVoiceConsentResponse updateVoiceConsent(OpenAiUpdateVoiceConsentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (isNullOrBlank(request.consentId())) {
            throw new IllegalArgumentException("Consent ID is required");
        }
        if (isNullOrBlank(request.name())) {
            throw new IllegalArgumentException("Name is required");
        }

        return withRetryMappingExceptions(
                () -> client.updateVoiceConsent(request).execute(), maxRetries);
    }

    public OpenAiDeleteVoiceConsentResponse deleteVoiceConsent(OpenAiVoiceConsentId request) {
        validate(request);

        return withRetryMappingExceptions(
                () -> client.deleteVoiceConsent(request).execute(), maxRetries);
    }

    private static void validate(OpenAiVoiceConsentId request) {
        if (request == null || isNullOrBlank(request.consentId())) {
            throw new IllegalArgumentException("Request and consent ID are required");
        }
    }

    public ModelProvider provider() {
        return OPEN_AI;
    }

    public static Builder builder() {
        for (OpenAiVoiceConsentModelBuilderFactory factory :
                loadFactories(OpenAiVoiceConsentModelBuilderFactory.class)) {
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

        public OpenAiVoiceConsentModel build() {
            return new OpenAiVoiceConsentModel(this);
        }
    }
}
