package dev.langchain4j.model.googleai;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscovery;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import dev.langchain4j.model.discovery.ModelType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Google AI Gemini implementation of {@link ModelDiscovery}.
 *
 * <p>Uses the Gemini Models API to dynamically discover available models.
 *
 * <p>Example:
 * <pre>{@code
 * GoogleAiGeminiModelDiscovery discovery = GoogleAiGeminiModelDiscovery.builder()
 *     .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
 *     .build();
 *
 * List<ModelDescription> models = discovery.discoverModels();
 * }</pre>
 */
public class GoogleAiGeminiModelDiscovery implements ModelDiscovery {

    private final GeminiService geminiService;

    private GoogleAiGeminiModelDiscovery(Builder builder) {
        this.geminiService = new GeminiService(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.baseUrl,
                builder.logRequestsAndResponses,
                builder.logRequests,
                builder.logResponses,
                builder.logger,
                builder.timeout);
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
        List<ModelDescription> allModels = new ArrayList<>();
        String pageToken = null;

        // Fetch all pages
        do {
            GeminiModelsListResponse response = geminiService.listModels(null, pageToken);
            if (response.models() != null) {
                List<ModelDescription> pageModels = response.models().stream()
                        .map(this::mapToModelDescription)
                        .collect(Collectors.toList());
                allModels.addAll(pageModels);
            }
            pageToken = response.nextPageToken();
        } while (pageToken != null);

        // Google Gemini doesn't support server-side filtering, so filter client-side
        if (filter != null && !filter.matchesAll()) {
            return filterModels(allModels, filter);
        }

        return allModels;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.GOOGLE_AI_GEMINI;
    }

    @Override
    public boolean supportsFiltering() {
        return false; // Google Gemini doesn't support server-side filtering
    }

    private ModelDescription mapToModelDescription(GeminiModelInfo modelInfo) {
        ModelDescription.Builder builder = ModelDescription.builder().provider(ModelProvider.GOOGLE_AI_GEMINI);

        // Model ID: extract from name (format: "models/gemini-1.5-pro")
        if (modelInfo.name() != null) {
            String id =
                    modelInfo.name().startsWith("models/") ? modelInfo.name().substring(7) : modelInfo.name();
            builder.id(id);
        }

        // Use display_name if available, otherwise use id
        if (modelInfo.displayName() != null && !modelInfo.displayName().isEmpty()) {
            builder.name(modelInfo.displayName());
        } else if (modelInfo.name() != null) {
            builder.name(modelInfo.name());
        }

        // Description
        if (modelInfo.description() != null) {
            builder.description(modelInfo.description());
        }

        // Context window (input token limit)
        if (modelInfo.inputTokenLimit() != null) {
            builder.contextWindow(modelInfo.inputTokenLimit());
        }

        // Max output tokens
        if (modelInfo.outputTokenLimit() != null) {
            builder.maxOutputTokens(modelInfo.outputTokenLimit());
        }

        // Determine model type based on supported generation methods
        if (modelInfo.supportedGenerationMethods() != null) {
            if (modelInfo.supportedGenerationMethods().contains("generateContent")) {
                builder.type(ModelType.CHAT);
            } else if (modelInfo.supportedGenerationMethods().contains("embedContent")) {
                builder.type(ModelType.EMBEDDING);
            }
        }

        return builder.build();
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
        private String baseUrl;
        private String apiKey;
        private boolean logRequestsAndResponses;
        private boolean logRequests;
        private boolean logResponses;
        private Logger logger;
        private Duration timeout;

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

        public Builder logRequestsAndResponses(boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public GoogleAiGeminiModelDiscovery build() {
            return new GoogleAiGeminiModelDiscovery(this);
        }
    }
}
