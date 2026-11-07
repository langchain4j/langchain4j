package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.moderation.Categories;
import dev.langchain4j.model.openai.internal.moderation.CategoryScores;
import dev.langchain4j.model.openai.internal.moderation.ModerationResult;
import dev.langchain4j.model.openai.spi.OpenAiModerationModelBuilderFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Represents an OpenAI moderation model, such as text-moderation-latest.
 */
public class OpenAiModerationModel implements ModerationModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Integer maxRetries;
    private final List<ModerationModelListener> listeners;

    public OpenAiModerationModel(OpenAiModerationModelBuilder builder) {

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
                .customHeaders(builder.customHeadersSupplier)
                .customQueryParams(builder.customQueryParams)
                .build();
        this.modelName = builder.modelName;
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.listeners = copy(builder.listeners);
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public List<ModerationModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OPEN_AI;
    }

    @Override
    public ModerationResponse doModerate(ModerationRequest moderationRequest) {
        dev.langchain4j.model.openai.internal.moderation.ModerationRequest request =
                dev.langchain4j.model.openai.internal.moderation.ModerationRequest.builder()
                        .model(moderationRequest.modelName())
                        .input(moderationRequest.texts())
                        .build();

        dev.langchain4j.model.openai.internal.moderation.ModerationResponse response =
                withRetryMappingExceptions(() -> client.moderation(request).execute(), maxRetries);

        List<String> texts = moderationRequest.texts();
        List<ModerationResult> results = response.results();
        int flaggedIndex = findFirstFlaggedIndex(results);

        Moderation moderation =
                flaggedIndex >= 0 ? Moderation.flagged(texts.get(flaggedIndex)) : Moderation.notFlagged();
        OpenAiModerationResponseMetadata metadata = createMetadata(response.id(), response.model(), texts, results);

        return ModerationResponse.builder()
                .moderation(moderation)
                .metadata(metadata.toMap())
                .typedMetadata(metadata)
                .build();
    }

    private static int findFirstFlaggedIndex(List<ModerationResult> results) {
        for (int i = 0; i < results.size(); i++) {
            if (Boolean.TRUE.equals(results.get(i).isFlagged())) {
                return i;
            }
        }
        return -1;
    }

    private static OpenAiModerationResponseMetadata createMetadata(
            String id, String model, List<String> texts, List<ModerationResult> results) {
        List<OpenAiModerationResultMetadata> resultMetadata = new ArrayList<>(results.size());

        for (int i = 0; i < results.size(); i++) {
            ModerationResult result = results.get(i);
            resultMetadata.add(OpenAiModerationResultMetadata.builder()
                    .text(i < texts.size() ? texts.get(i) : null)
                    .categories(toMap(result.categories()))
                    .categoryScores(toMap(result.categoryScores()))
                    .categoryAppliedInputTypes(toMap(result.categoryAppliedInputTypes()))
                    .build());
        }

        return OpenAiModerationResponseMetadata.builder()
                .id(id)
                .model(model)
                .results(resultMetadata)
                .build();
    }

    private static Map<String, Boolean> toMap(Categories categories) {
        Map<String, Boolean> map = new LinkedHashMap<>();

        if (categories == null) {
            return map;
        }

        putIfNotNull(map, "harassment", categories.harassment());
        putIfNotNull(map, "harassment/threatening", categories.harassmentThreatening());
        putIfNotNull(map, "hate", categories.hate());
        putIfNotNull(map, "hate/threatening", categories.hateThreatening());
        putIfNotNull(map, "illicit", categories.illicit());
        putIfNotNull(map, "illicit/violent", categories.illicitViolent());
        putIfNotNull(map, "self-harm", categories.selfHarm());
        putIfNotNull(map, "self-harm/intent", categories.selfHarmIntent());
        putIfNotNull(map, "self-harm/instructions", categories.selfHarmInstructions());
        putIfNotNull(map, "sexual", categories.sexual());
        putIfNotNull(map, "sexual/minors", categories.sexualMinors());
        putIfNotNull(map, "violence", categories.violence());
        putIfNotNull(map, "violence/graphic", categories.violenceGraphic());

        return map;
    }

    private static Map<String, Double> toMap(CategoryScores categoryScores) {
        Map<String, Double> map = new LinkedHashMap<>();

        if (categoryScores == null) {
            return map;
        }

        putIfNotNull(map, "harassment", categoryScores.harassment());
        putIfNotNull(map, "harassment/threatening", categoryScores.harassmentThreatening());
        putIfNotNull(map, "hate", categoryScores.hate());
        putIfNotNull(map, "hate/threatening", categoryScores.hateThreatening());
        putIfNotNull(map, "illicit", categoryScores.illicit());
        putIfNotNull(map, "illicit/violent", categoryScores.illicitViolent());
        putIfNotNull(map, "self-harm", categoryScores.selfHarm());
        putIfNotNull(map, "self-harm/intent", categoryScores.selfHarmIntent());
        putIfNotNull(map, "self-harm/instructions", categoryScores.selfHarmInstructions());
        putIfNotNull(map, "sexual", categoryScores.sexual());
        putIfNotNull(map, "sexual/minors", categoryScores.sexualMinors());
        putIfNotNull(map, "violence", categoryScores.violence());
        putIfNotNull(map, "violence/graphic", categoryScores.violenceGraphic());

        return map;
    }

    private static Map<String, List<String>> toMap(Map<String, List<String>> categoryAppliedInputTypes) {
        Map<String, List<String>> map = new LinkedHashMap<>();

        if (categoryAppliedInputTypes == null) {
            return map;
        }

        categoryAppliedInputTypes.forEach((key, value) -> putIfNotNull(map, key, value));

        return map;
    }

    private static <T> void putIfNotNull(Map<String, T> map, String key, T value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    public static OpenAiModerationModelBuilder builder() {
        for (OpenAiModerationModelBuilderFactory factory : loadFactories(OpenAiModerationModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiModerationModelBuilder();
    }

    public static class OpenAiModerationModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;

        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private Map<String, String> customQueryParams;
        private List<ModerationModelListener> listeners;

        public OpenAiModerationModelBuilder() {
            // This is public so it can be extended
        }

        public OpenAiModerationModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OpenAiModerationModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiModerationModelBuilder modelName(OpenAiModerationModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiModerationModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiModerationModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiModerationModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiModerationModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public OpenAiModerationModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiModerationModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiModerationModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OpenAiModerationModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public OpenAiModerationModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public OpenAiModerationModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         * For example, this is useful for OAuth2 tokens that expire and need refreshing.
         */
        public OpenAiModerationModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        public OpenAiModerationModelBuilder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        /**
         * Sets the listeners for this moderation model.
         *
         * @param listeners the listeners.
         * @return {@code this}.
         */
        public OpenAiModerationModelBuilder listeners(List<ModerationModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OpenAiModerationModel build() {
            return new OpenAiModerationModel(this);
        }
    }
}
