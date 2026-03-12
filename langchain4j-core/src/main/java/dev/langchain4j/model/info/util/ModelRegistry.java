package dev.langchain4j.model.info.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.info.ModelInfo;
import dev.langchain4j.model.info.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry for managing and querying AI model providers and their
 * models.
 */
public class ModelRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRegistry.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_API_URL = "https://models.dev/api.json";
    private final Map<String, Provider> providers;

    private String apiUrl;

    static {
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private ModelRegistry(Map<String, Provider> providers) {
        this.providers = providers;
        this.apiUrl = DEFAULT_API_URL;
    }

    public static ModelRegistry fromApi() {
        try {
            return fromApi(DEFAULT_API_URL);
        } catch (Exception e) {
            LOGGER.error("Error occurred while initializing ModelRegistry {}", e);
            return new ModelRegistry(Collections.emptyMap());
        }
    }

    /**
     * Load providers from a custom API endpoint.
     *
     * @param apiUrl the API URL
     * @return ModelRegistry instance
     * @throws IOException          if loading fails
     * @throws InterruptedException if the request is interrupted
     */
    public static ModelRegistry fromApi(String apiUrl) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request =
                HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch API data: HTTP " + response.statusCode());
        }

        ModelRegistry registry = fromJson(response.body());
        registry.apiUrl = apiUrl;
        return registry;
    }

    /**
     * Load providers from a JSON string.
     *
     * @param json the JSON string
     * @return ModelRegistry instance
     * @throws IOException if parsing fails
     */
    public static ModelRegistry fromJson(String json) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> data = OBJECT_MAPPER.readValue(json, Map.class);

        Map<String, Provider> providers = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            String providerId = entry.getKey();
            Provider provider = OBJECT_MAPPER.convertValue(entry.getValue(), Provider.class);
            provider.setId(providerId);
            providers.put(providerId, provider);
        }

        return new ModelRegistry(providers);
    }

    /**
     * Load providers from a classpath resource.
     *
     * @param resourcePath the resource path
     * @return ModelRegistry instance
     * @throws IOException if loading fails
     */
    public static ModelRegistry fromResource(String resourcePath) throws IOException {
        try (InputStream is = ModelRegistry.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> data = OBJECT_MAPPER.readValue(is, Map.class);

            Map<String, Provider> providers = new LinkedHashMap<>();

            for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
                String providerId = entry.getKey();
                Provider provider = OBJECT_MAPPER.convertValue(entry.getValue(), Provider.class);
                provider.setId(providerId);
                providers.put(providerId, provider);
            }

            return new ModelRegistry(providers);
        }
    }

    // Provider methods
    public Provider getProvider(String providerId) {
        return providers.get(providerId);
    }

    public List<Provider> getAllProviders() {
        return new ArrayList<>(providers.values());
    }

    public List<String> getProviderIds() {
        return new ArrayList<>(providers.keySet());
    }

    public int getProviderCount() {
        return providers.size();
    }

    // Model lookup methods

    /**
     * Get a model by provider ID and model ID.
     *
     * @param providerId the provider ID
     * @param modelId    the model ID
     * @return the model, or null if not found
     */
    public ModelInfo getModelInfo(String providerId, String modelId) {
        Provider provider = providers.get(providerId);
        return provider != null ? provider.getModel(modelId) : null;
    }

    /**
     * Get a model by provider and model ID using a single method call. This is a
     * convenience method that combines provider and model lookup.
     *
     * @param provider the provider instance
     * @param modelId  the model ID
     * @return the model, or null if not found
     * @throws IllegalArgumentException if provider is null
     */
    public ModelInfo getModelInfo(Provider provider, String modelId) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }
        return provider.getModel(modelId);
    }

    public List<ModelInfo> getAllModelsInfo() {
        return providers.values().stream()
                .flatMap(p -> p.getAllModels().stream())
                .collect(Collectors.toList());
    }

    public List<ModelInfo> getModelsByProvider(String providerId) {
        Provider provider = providers.get(providerId);
        return provider != null ? provider.getAllModels() : List.of();
    }

    public List<ModelInfo> getModelsByFamily(String family) {
        return getAllModelsInfo().stream()
                .filter(m -> family.equals(m.getFamily()))
                .collect(Collectors.toList());
    }

    public List<ModelInfo> getFreeModels() {
        return getAllModelsInfo().stream().filter(ModelInfo::isFree).collect(Collectors.toList());
    }

    public List<ModelInfo> getReasoningModels() {
        return getAllModelsInfo().stream().filter(ModelInfo::supportsReasoning).collect(Collectors.toList());
    }

    public List<ModelInfo> getMultimodalModels() {
        return getAllModelsInfo().stream().filter(ModelInfo::isMultimodal).collect(Collectors.toList());
    }

    public List<ModelInfo> getOpenWeightModels() {
        return getAllModelsInfo().stream().filter(ModelInfo::hasOpenWeights).collect(Collectors.toList());
    }

    public List<ModelInfo> getModelsWithToolCalls() {
        return getAllModelsInfo().stream().filter(ModelInfo::supportsToolCalls).collect(Collectors.toList());
    }

    // Search methods
    public List<ModelInfo> searchByName(String query) {
        String lowerQuery = query.toLowerCase();
        return getAllModelsInfo().stream()
                .filter(m -> m.getName().toLowerCase().contains(lowerQuery)
                        || m.getId().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    public List<ModelInfo> getModelsWithLargeContext(int minContextSize) {
        return getAllModelsInfo().stream()
                .filter(m -> m.getLimit() != null
                        && m.getLimit().getContext() != null
                        && m.getLimit().getContext() >= minContextSize)
                .collect(Collectors.toList());
    }

    public List<ModelInfo> getModelsBelowCost(double maxInputCost, double maxOutputCost) {
        return getAllModelsInfo().stream()
                .filter(m -> m.getCost() != null
                        && (m.getCost().getInput() == null || m.getCost().getInput() <= maxInputCost)
                        && (m.getCost().getOutput() == null || m.getCost().getOutput() <= maxOutputCost))
                .collect(Collectors.toList());
    }

    // Statistics methods
    public int getTotalModelCount() {
        return getAllModelsInfo().size();
    }

    public Map<String, Long> getModelCountByProvider() {
        return providers.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e ->
                (long) e.getValue().getModelCount()));
    }

    public Map<String, Long> getModelCountByFamily() {
        return getAllModelsInfo().stream().collect(Collectors.groupingBy(ModelInfo::getFamily, Collectors.counting()));
    }

    public double getAverageInputCost() {
        return getAllModelsInfo().stream()
                .filter(m -> m.getCost() != null && m.getCost().getInput() != null)
                .mapToDouble(m -> m.getCost().getInput())
                .average()
                .orElse(0.0);
    }

    public double getAverageOutputCost() {
        return getAllModelsInfo().stream()
                .filter(m -> m.getCost() != null && m.getCost().getOutput() != null)
                .mapToDouble(m -> m.getCost().getOutput())
                .average()
                .orElse(0.0);
    }

    // Refresh methods

    /**
     * Refresh the model data from the API. This will reload all providers and
     * models from the original API URL.
     *
     * @throws IOException                   if loading fails
     * @throws InterruptedException          if the request is interrupted
     * @throws UnsupportedOperationException if the registry was not loaded from an
     *                                       API
     */
    public void refresh() throws IOException, InterruptedException {
        if (apiUrl == null) {
            throw new UnsupportedOperationException("Cannot refresh: registry was not loaded from an API. "
                    + "Use refreshFrom(url) to specify an API endpoint.");
        }
        refreshFrom(apiUrl);
    }

    /**
     * Refresh the model data from a specific API URL. This will reload all
     * providers and models, replacing the existing data.
     *
     * @param apiUrl the API URL to refresh from
     * @throws IOException          if loading fails
     * @throws InterruptedException if the request is interrupted
     */
    public void refreshFrom(String apiUrl) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request =
                HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch API data: HTTP " + response.statusCode());
        }

        // Parse the new data
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> data = OBJECT_MAPPER.readValue(response.body(), Map.class);

        // Clear existing providers
        providers.clear();

        // Load new providers
        for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            String providerId = entry.getKey();
            Provider provider = OBJECT_MAPPER.convertValue(entry.getValue(), Provider.class);
            provider.setId(providerId);
            providers.put(providerId, provider);
        }

        // Update the API URL
        this.apiUrl = apiUrl;
    }

    /**
     * Get the API URL that this registry is using (if loaded from API).
     *
     * @return the API URL, or null if not loaded from an API
     */
    public String getApiUrl() {
        return apiUrl;
    }

    @Override
    public String toString() {
        return "ModelRegistry{" + "providers=" + providers.size() + ", totalModels=" + getTotalModelCount()
                + ", apiUrl='" + apiUrl + '\'' + '}';
    }
}
