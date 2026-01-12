package dev.langchain4j.model.mistralai;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelCard;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelResponse;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.MISTRAL_AI;

/**
 * Mistral AI implementation of {@link ModelCatalog}.
 *
 * <p>Example:
 * <pre>{@code
 * MistralAiModelCatalog catalog = MistralAiModelCatalog.builder()
 *     .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
 *     .build();
 *
 * List<ModelDescription> models = catalog.listModels();
 * }</pre>
 *
 * @see MistralAiModels
 */
public class MistralAiModelCatalog implements ModelCatalog {

    private final MistralAiClient client;

    private MistralAiModelCatalog(Builder builder) {
        this.client = MistralAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(builder.apiKey)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> listModels() {
        MistralAiModelResponse response = client.listModels();
        List<ModelDescription> models = response.getData().stream()
                .map(this::mapFromMistralAiModelCard)
                .toList();
        return models;
    }

    @Override
    public ModelProvider provider() {
        return MISTRAL_AI;
    }

    private ModelDescription mapFromMistralAiModelCard(MistralAiModelCard card) {
        return ModelDescription.builder()
                .name(card.getId())
                .provider(MISTRAL_AI)
                .owner(card.getOwnerBy())
                .createdAt(card.getCreated() != null ? Instant.ofEpochSecond(card.getCreated()) : null)
                .build();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

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

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public MistralAiModelCatalog build() {
            return new MistralAiModelCatalog(this);
        }
    }
}
