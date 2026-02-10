package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.mistralai.internal.api.MistralAiCategories;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModerationRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModerationResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModerationResult;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;

public class MistralAiModerationModel implements ModerationModel {

    private final MistralAiClient client;
    private final String modelName;
    private final Integer maxRetries;
    private final List<ModerationModelListener> listeners;

    public MistralAiModerationModel(Builder builder) {
        this.client = MistralAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(builder.apiKey)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.listeners = copyIfNotNull(builder.listeners);
    }

    @Override
    public List<ModerationModelListener> listeners() {
        return listeners != null ? listeners : List.of();
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.MISTRAL_AI;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public ModerationResponse doModerate(ModerationRequest moderationRequest) {
        List<String> inputs = ModerationModel.toInputs(moderationRequest);
        return moderateInternal(inputs);
    }

    private ModerationResponse moderateInternal(List<String> inputs) {

        MistralAiModerationRequest request = MistralAiModerationRequest.builder()
                .model(modelName)
                .input(inputs)
                .build();

        MistralAiModerationResponse response = withRetryMappingExceptions(() -> client.moderation(request), maxRetries);

        int i = 0;
        for (MistralAiModerationResult moderationResult : response.results()) {

            if (isAnyCategoryFlagged(moderationResult.getCategories())) {
                return ModerationResponse.builder()
                        .moderation(Moderation.flagged(inputs.get(i)))
                        .build();
            }
            i++;
        }

        return ModerationResponse.builder().moderation(Moderation.notFlagged()).build();
    }

    private boolean isAnyCategoryFlagged(MistralAiCategories categories) {
        return (categories.getSexual() != null && categories.getSexual())
                || (categories.getHateAndDiscrimination() != null && categories.getHateAndDiscrimination())
                || (categories.getViolenceAndThreats() != null && categories.getViolenceAndThreats())
                || (categories.getDangerousAndCriminalContent() != null && categories.getDangerousAndCriminalContent())
                || (categories.getSelfHarm() != null && categories.getSelfHarm())
                || (categories.getHealth() != null && categories.getHealth())
                || (categories.getLaw() != null && categories.getLaw())
                || (categories.getPii() != null && categories.getPii());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private String modelName;
        private Integer maxRetries;
        private List<ModerationModelListener> listeners;

        /**
         * @param httpClientBuilder the HTTP client builder to use for creating the HTTP client
         * @return {@code this}.
         */
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

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
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

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the listeners for this moderation model.
         *
         * @param listeners the listeners.
         * @return {@code this}.
         */
        public Builder listeners(List<ModerationModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public MistralAiModerationModel build() {
            return new MistralAiModerationModel(this);
        }
    }
}
