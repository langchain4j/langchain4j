package dev.langchain4j.model.mistralai;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelCard;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModelResponse;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;

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
 */
public class MistralAiModelCatalog implements ModelCatalog {

    private final MistralAiClient client;

    private MistralAiModelCatalog(Builder builder) {
        this.client = MistralAiClient.builder()
                .baseUrl(builder.baseUrl)
                .apiKey(builder.apiKey)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logger(builder.logger)
                .httpClientBuilder(builder.httpClientBuilder)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> listModels() {
        MistralAiModelResponse response = client.listModels();
        List<ModelDescription> models =
                response.getData().stream().map(this::mapFromMistralAiModelCard).collect(Collectors.toList());

        return models;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.MISTRAL_AI;
    }

    private ModelDescription mapFromMistralAiModelCard(MistralAiModelCard card) {
        return ModelDescription.builder()
                .name(card.id())
                .displayName(card.id()) // Mistral AI uses id as name
                .provider(ModelProvider.MISTRAL_AI)
                .owner(card.ownerBy())
                .createdAt(card.created() != null ? Instant.ofEpochSecond(card.created()) : null)
                .build();
    }

    public static class Builder {
        private String baseUrl = "https://api.mistral.ai/v1";
        private String apiKey;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private HttpClientBuilder httpClientBuilder;

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

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public MistralAiModelCatalog build() {
            return new MistralAiModelCatalog(this);
        }
    }
}
