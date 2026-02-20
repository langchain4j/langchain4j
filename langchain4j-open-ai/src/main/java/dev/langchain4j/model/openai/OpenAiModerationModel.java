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
import dev.langchain4j.model.openai.internal.moderation.ModerationResult;
import dev.langchain4j.model.openai.spi.OpenAiModerationModelBuilderFactory;
import java.time.Duration;
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
        List<String> inputs = ModerationModel.toInputs(moderationRequest);
        return moderateInternal(inputs);
    }

    private ModerationResponse moderateInternal(List<String> inputs) {

        dev.langchain4j.model.openai.internal.moderation.ModerationRequest request =
                dev.langchain4j.model.openai.internal.moderation.ModerationRequest.builder()
                        .model(modelName)
                        .input(inputs)
                        .build();

        dev.langchain4j.model.openai.internal.moderation.ModerationResponse response =
                withRetryMappingExceptions(() -> client.moderation(request).execute(), maxRetries);

        int i = 0;
        for (ModerationResult moderationResult : response.results()) {
            if (Boolean.TRUE.equals(moderationResult.isFlagged())) {
                return ModerationResponse.builder()
                        .moderation(Moderation.flagged(inputs.get(i)))
                        .build();
            }
            i++;
        }

        return ModerationResponse.builder().moderation(Moderation.notFlagged()).build();
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
