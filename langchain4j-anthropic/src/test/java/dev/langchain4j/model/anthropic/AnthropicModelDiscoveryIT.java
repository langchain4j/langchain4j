package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicModelDiscoveryIT {

    private static final String API_KEY = System.getenv("ANTHROPIC_API_KEY");

    @Test
    void should_discover_anthropic_models() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getId() != null);
        assertThat(models).allMatch(m -> m.getName() != null);
        assertThat(models).allMatch(m -> m.getProvider() == ModelProvider.ANTHROPIC);
    }

    @Test
    void should_return_anthropic_provider() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.ANTHROPIC);
    }

    @Test
    void should_not_support_server_side_filtering() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        assertThat(discovery.supportsFiltering()).isFalse();
    }

    @Test
    void should_discover_models_with_null_filter() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        List<ModelDescription> modelsWithoutFilter = discovery.discoverModels();
        List<ModelDescription> modelsWithNullFilter = discovery.discoverModels(null);

        assertThat(modelsWithNullFilter).hasSameSizeAs(modelsWithoutFilter);
    }

    @Test
    void should_filter_by_name_pattern() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .namePattern(".*laude.*")
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        assertThat(models).allMatch(m -> m.getName().toLowerCase().contains("laude"));
    }

    @Test
    void should_have_display_name() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getName() != null);
    }

    @Test
    void should_have_creation_timestamp() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder()
            .apiKey(API_KEY)
            .build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.getCreatedAt() != null);
    }
}
