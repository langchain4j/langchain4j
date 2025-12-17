package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import dev.langchain4j.model.discovery.ModelType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiModelDiscoveryIT {

    private static final String API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void should_discover_gemini_models() {
        GoogleAiGeminiModelDiscovery discovery = GoogleAiGeminiModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getId() != null);
        assertThat(models).allMatch(m -> m.getName() != null);
        assertThat(models).allMatch(m -> m.getProvider() == ModelProvider.GOOGLE);
    }

    @Test
    void should_return_google_provider() {
        GoogleAiGeminiModelDiscovery discovery = GoogleAiGeminiModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.GOOGLE);
    }

    @Test
    void should_not_support_server_side_filtering() {
        GoogleAiGeminiModelDiscovery discovery = GoogleAiGeminiModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        assertThat(discovery.supportsFiltering()).isFalse();
    }

    @Test
    void should_discover_models_with_null_filter() {
        GoogleAiGeminiModelDiscovery discovery = GoogleAiGeminiModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        List<ModelDescription> modelsWithoutFilter = discovery.discoverModels();
        List<ModelDescription> modelsWithNullFilter = discovery.discoverModels(null);

        assertThat(modelsWithNullFilter).hasSameSizeAs(modelsWithoutFilter);
    }

    @Test
    void should_filter_by_name_pattern() {
        GoogleAiGeminiModelDiscovery discovery = GoogleAiGeminiModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .namePattern(".*[Gg]emini.*")
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        assertThat(models).allMatch(m -> m.getName().toLowerCase().contains("gemini"));
    }

    @Test
    void should_filter_by_type() {
        GoogleAiGeminiModelDiscovery discovery = GoogleAiGeminiModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .types(Set.of(ModelType.CHAT))
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getType() == ModelType.CHAT);
    }

    @Test
    void should_have_context_window_information() {
        GoogleAiGeminiModelDiscovery discovery = GoogleAiGeminiModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.getContextWindow() != null && m.getContextWindow() > 0);
    }

    @Test
    void should_have_max_output_tokens() {
        GoogleAiGeminiModelDiscovery discovery = GoogleAiGeminiModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.getMaxOutputTokens() != null && m.getMaxOutputTokens() > 0);
    }

    @Test
    void should_filter_by_context_window() {
        GoogleAiGeminiModelDiscovery discovery = GoogleAiGeminiModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .minContextWindow(100000)
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        // For models with context window information, verify filtering
        models.stream()
            .filter(m -> m.getContextWindow() != null)
            .forEach(m -> assertThat(m.getContextWindow())
                .isGreaterThanOrEqualTo(100000));
    }
}
