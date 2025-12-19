package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicModelDiscoveryIT {

    private static final String API_KEY = System.getenv("ANTHROPIC_API_KEY");

    @Test
    void should_discover_anthropic_models() {
        AnthropicModelDiscovery discovery =
                AnthropicModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null);
        assertThat(models).allMatch(m -> m.displayName() != null);
        assertThat(models).allMatch(m -> m.provider() == ModelProvider.ANTHROPIC);
    }

    @Test
    void should_return_anthropic_provider() {
        AnthropicModelDiscovery discovery =
                AnthropicModelDiscovery.builder().apiKey(API_KEY).build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.ANTHROPIC);
    }

    @Test
    void should_have_display_name() {
        AnthropicModelDiscovery discovery =
                AnthropicModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.displayName() != null);
    }

    @Test
    void should_have_creation_timestamp() {
        AnthropicModelDiscovery discovery =
                AnthropicModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.createdAt() != null);
    }
}
