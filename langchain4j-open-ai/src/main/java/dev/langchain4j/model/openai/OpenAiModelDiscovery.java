package dev.langchain4j.model.openai;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscovery;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.models.ModelsListResponse;
import dev.langchain4j.model.openai.internal.models.OpenAiModelInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * OpenAI implementation of {@link ModelDiscovery}.
 *
 * <p>Example:
 * <pre>{@code
 * OpenAiModelDiscovery discovery = OpenAiModelDiscovery.builder()
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .build();
 *
 * List<ModelDescription> models = discovery.discoverModels();
 * }</pre>
 */
public class OpenAiModelDiscovery implements ModelDiscovery {

    private final OpenAiClient client;

    private OpenAiModelDiscovery(Builder builder) {
        this.client = OpenAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .projectId(builder.projectId)
                .connectTimeout(builder.connectTimeout)
                .readTimeout(builder.readTimeout)
                .userAgent(builder.userAgent)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logger(builder.logger)
                .customHeaders(builder.customHeaders)
                .customQueryParams(builder.customQueryParams)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> discoverModels() {
        return discoverModels(null);
    }

    @Override
    public List<ModelDescription> discoverModels(ModelDiscoveryFilter filter) {
        ModelsListResponse response = client.listModels().execute();
        List<ModelDescription> models =
                response.getData().stream().map(this::mapToModelDescription).collect(Collectors.toList());

        // OpenAI doesn't support server-side filtering, so filter client-side
        if (filter != null && !filter.matchesAll()) {
            return filterModels(models, filter);
        }

        return models;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OPEN_AI;
    }

    @Override
    public boolean supportsFiltering() {
        return false; // OpenAI doesn't support server-side filtering
    }

    private ModelDescription mapToModelDescription(OpenAiModelInfo modelInfo) {
        return ModelDescription.builder()
                .id(modelInfo.getId())
                .name(modelInfo.getId()) // OpenAI uses id as name
                .provider(ModelProvider.OPEN_AI)
                .owner(modelInfo.getOwnedBy())
                .createdAt(modelInfo.getCreated() != null ? Instant.ofEpochSecond(modelInfo.getCreated()) : null)
                .build();
    }

    private List<ModelDescription> filterModels(List<ModelDescription> models, ModelDiscoveryFilter filter) {
        return models.stream().filter(model -> matchesFilter(model, filter)).collect(Collectors.toList());
    }

    private boolean matchesFilter(ModelDescription model, ModelDiscoveryFilter filter) {
        // Filter by type
        if (filter.getTypes() != null && !filter.getTypes().isEmpty()) {
            if (model.getType() == null || !filter.getTypes().contains(model.getType())) {
                return false;
            }
        }

        // Filter by required capabilities
        if (filter.getRequiredCapabilities() != null
                && !filter.getRequiredCapabilities().isEmpty()) {
            if (model.getCapabilities() == null
                    || !model.getCapabilities().containsAll(filter.getRequiredCapabilities())) {
                return false;
            }
        }

        // Filter by minimum context window
        if (filter.getMinContextWindow() != null) {
            if (model.getContextWindow() == null || model.getContextWindow() < filter.getMinContextWindow()) {
                return false;
            }
        }

        // Filter by maximum context window
        if (filter.getMaxContextWindow() != null) {
            if (model.getContextWindow() == null || model.getContextWindow() > filter.getMaxContextWindow()) {
                return false;
            }
        }

        // Filter by name pattern
        if (filter.getNamePattern() != null) {
            Pattern pattern = Pattern.compile(filter.getNamePattern());
            if (!pattern.matcher(model.getName()).matches()) {
                return false;
            }
        }

        // Filter by deprecated status
        if (filter.getIncludeDeprecated() != null && !filter.getIncludeDeprecated()) {
            if (Boolean.TRUE.equals(model.isDeprecated())) {
                return false;
            }
        }

        return true;
    }

    public static class Builder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl = "https://api.openai.com/v1/";
        private String apiKey;
        private String organizationId;
        private String projectId;
        private Duration connectTimeout;
        private Duration readTimeout;
        private String userAgent;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Map<String, String> customHeaders;
        private Map<String, String> customQueryParams;

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

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
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

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        public OpenAiModelDiscovery build() {
            return new OpenAiModelDiscovery(this);
        }
    }
}
