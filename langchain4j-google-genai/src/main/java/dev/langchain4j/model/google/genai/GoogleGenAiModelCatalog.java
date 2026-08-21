package dev.langchain4j.model.google.genai;

import static dev.langchain4j.model.ModelProvider.GOOGLE_GENAI;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.ListModelsConfig;
import com.google.genai.types.Model;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.catalog.ModelType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Google GenAI implementation of {@link ModelCatalog}.
 *
 * <p>Uses the Gemini Models API to dynamically discover available models.
 *
 * <p>Example:
 * <pre>{@code
 * GoogleGenAiModelCatalog catalog = GoogleGenAiModelCatalog.builder()
 *     .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
 *     .build();
 *
 * List<ModelDescription> models = catalog.listModels();
 * }</pre>
 */
public class GoogleGenAiModelCatalog implements ModelCatalog {

    private final Client client;

    private GoogleGenAiModelCatalog(Builder builder) {
        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        builder.credentials,
                        builder.projectId,
                        builder.location,
                        builder.timeout,
                        builder.customHeaders,
                        builder.apiEndpoint);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> listModels() {
        List<ModelDescription> allModels = new ArrayList<>();

        client.models.list(ListModelsConfig.builder().build()).forEach(modelInfo -> {
            allModels.add(mapToModelDescription(modelInfo));
        });

        return allModels;
    }

    @Override
    public ModelProvider provider() {
        return GOOGLE_GENAI;
    }

    private ModelDescription mapToModelDescription(Model modelInfo) {
        ModelDescription.Builder builder = ModelDescription.builder().provider(GOOGLE_GENAI);

        if (modelInfo.name().isPresent()) {
            String name = modelInfo.name().get();
            String id = name.startsWith("models/") ? name.substring(7) : name;
            builder.name(id);
        }

        if (modelInfo.displayName().isPresent()
                && !modelInfo.displayName().get().isEmpty()) {
            builder.displayName(modelInfo.displayName().get());
        }

        if (modelInfo.description().isPresent()) {
            builder.description(modelInfo.description().get());
        }

        if (modelInfo.inputTokenLimit().isPresent()) {
            builder.maxInputTokens(modelInfo.inputTokenLimit().get());
        }

        if (modelInfo.outputTokenLimit().isPresent()) {
            builder.maxOutputTokens(modelInfo.outputTokenLimit().get());
        }

        // Determine model type based on supported generation methods
        if (modelInfo.supportedActions().isPresent()) {
            List<String> actions = modelInfo.supportedActions().get();
            if (actions.contains("generateContent")) {
                builder.type(ModelType.CHAT);
            } else if (actions.contains("embedContent")) {
                builder.type(ModelType.EMBEDDING);
            }
        }

        return builder.build();
    }

    public static class Builder {

        private String apiKey;
        private GoogleCredentials credentials;
        private String projectId;
        private String location;
        private Duration timeout;
        private String apiEndpoint;
        private Map<String, String> customHeaders;
        private Client client;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder credentials(GoogleCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public GoogleGenAiModelCatalog build() {
            return new GoogleGenAiModelCatalog(this);
        }
    }
}
