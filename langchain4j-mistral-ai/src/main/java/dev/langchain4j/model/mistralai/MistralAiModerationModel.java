package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.mistralai.internal.api.MistralAiCategories;
import dev.langchain4j.model.mistralai.internal.api.MistralAiCategoryScores;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        this.listeners = copy(builder.listeners);
    }

    @Override
    public List<ModerationModelListener> listeners() {
        return listeners;
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
        MistralAiModerationRequest request = MistralAiModerationRequest.builder()
                .model(moderationRequest.modelName())
                .input(moderationRequest.texts())
                .build();

        MistralAiModerationResponse response = withRetryMappingExceptions(() -> client.moderation(request), maxRetries);

        List<String> texts = moderationRequest.texts();
        List<MistralAiModerationResult> results = response.results();
        int flaggedIndex = findFirstFlaggedIndex(results);

        Moderation moderation =
                flaggedIndex >= 0 ? Moderation.flagged(texts.get(flaggedIndex)) : Moderation.notFlagged();
        MistralAiModerationResponseMetadata metadata = createMetadata(response.id(), response.model(), texts, results);

        return ModerationResponse.builder()
                .moderation(moderation)
                .metadata(metadata.toMap())
                .typedMetadata(metadata)
                .build();
    }

    private int findFirstFlaggedIndex(List<MistralAiModerationResult> results) {
        for (int i = 0; i < results.size(); i++) {
            MistralAiCategories categories = results.get(i).getCategories();
            if (categories != null && isAnyCategoryFlagged(categories)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isAnyCategoryFlagged(MistralAiCategories categories) {
        return Boolean.TRUE.equals(categories.getSexual())
                || Boolean.TRUE.equals(categories.getHateAndDiscrimination())
                || Boolean.TRUE.equals(categories.getViolenceAndThreats())
                || Boolean.TRUE.equals(categories.getDangerousAndCriminalContent())
                || Boolean.TRUE.equals(categories.getDangerous())
                || Boolean.TRUE.equals(categories.getCriminal())
                || Boolean.TRUE.equals(categories.getSelfHarm())
                || Boolean.TRUE.equals(categories.getHealth())
                || Boolean.TRUE.equals(categories.getFinancial())
                || Boolean.TRUE.equals(categories.getLaw())
                || Boolean.TRUE.equals(categories.getPii())
                || Boolean.TRUE.equals(categories.getJailbreaking());
    }

    private static MistralAiModerationResponseMetadata createMetadata(
            String id, String model, List<String> texts, List<MistralAiModerationResult> results) {
        List<MistralAiModerationResultMetadata> resultMetadata = new ArrayList<>(results.size());

        for (int i = 0; i < results.size(); i++) {
            MistralAiModerationResult result = results.get(i);
            resultMetadata.add(MistralAiModerationResultMetadata.builder()
                    .text(i < texts.size() ? texts.get(i) : null)
                    .categories(toMap(result.getCategories()))
                    .categoryScores(toMap(result.getCategoryScores()))
                    .build());
        }

        return MistralAiModerationResponseMetadata.builder()
                .id(id)
                .model(model)
                .results(resultMetadata)
                .build();
    }

    private static Map<String, Boolean> toMap(MistralAiCategories categories) {
        Map<String, Boolean> map = new LinkedHashMap<>();

        if (categories == null) {
            return map;
        }

        putIfNotNull(map, "sexual", categories.getSexual());
        putIfNotNull(map, "hate_and_discrimination", categories.getHateAndDiscrimination());
        putIfNotNull(map, "violence_and_threats", categories.getViolenceAndThreats());
        putIfNotNull(map, "dangerous_and_criminal_content", categories.getDangerousAndCriminalContent());
        putIfNotNull(map, "dangerous", categories.getDangerous());
        putIfNotNull(map, "criminal", categories.getCriminal());
        putIfNotNull(map, "selfharm", categories.getSelfHarm());
        putIfNotNull(map, "health", categories.getHealth());
        putIfNotNull(map, "financial", categories.getFinancial());
        putIfNotNull(map, "law", categories.getLaw());
        putIfNotNull(map, "pii", categories.getPii());
        putIfNotNull(map, "jailbreaking", categories.getJailbreaking());

        return map;
    }

    private static Map<String, Double> toMap(MistralAiCategoryScores categoryScores) {
        Map<String, Double> map = new LinkedHashMap<>();

        if (categoryScores == null) {
            return map;
        }

        putIfNotNull(map, "sexual", categoryScores.getSexual());
        putIfNotNull(map, "hate_and_discrimination", categoryScores.getHateAndDiscrimination());
        putIfNotNull(map, "violence_and_threats", categoryScores.getViolenceAndThreats());
        putIfNotNull(map, "dangerous_and_criminal_content", categoryScores.getDangerousAndCriminalContent());
        putIfNotNull(map, "dangerous", categoryScores.getDangerous());
        putIfNotNull(map, "criminal", categoryScores.getCriminal());
        putIfNotNull(map, "selfharm", categoryScores.getSelfHarm());
        putIfNotNull(map, "health", categoryScores.getHealth());
        putIfNotNull(map, "financial", categoryScores.getFinancial());
        putIfNotNull(map, "law", categoryScores.getLaw());
        putIfNotNull(map, "pii", categoryScores.getPii());
        putIfNotNull(map, "jailbreaking", categoryScores.getJailbreaking());

        return map;
    }

    private static <T> void putIfNotNull(Map<String, T> map, String key, T value) {
        if (value != null) {
            map.put(key, value);
        }
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
