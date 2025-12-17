package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import dev.langchain4j.model.discovery.ModelType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnthropicModelDiscoveryTest {

    @Test
    void should_discover_anthropic_models() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder().build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getProvider() == ModelProvider.ANTHROPIC);
        assertThat(models).allMatch(m -> m.getType() == ModelType.CHAT);
        assertThat(models).anyMatch(m -> m.getId().contains("claude-3-5-sonnet"));
        assertThat(models).anyMatch(m -> m.getId().contains("claude-3-opus"));
    }

    @Test
    void should_return_anthropic_provider() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder().build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.ANTHROPIC);
    }

    @Test
    void should_not_support_server_side_filtering() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder().build();

        assertThat(discovery.supportsFiltering()).isFalse();
    }

    @Test
    void should_filter_by_context_window() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder().build();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .minContextWindow(200000)
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getContextWindow() >= 200000);
    }

    @Test
    void should_filter_by_name_pattern() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder().build();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .namePattern(".*Opus.*")
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getName().contains("Opus"));
    }

    @Test
    void should_include_pricing_information() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder().build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).allMatch(m -> m.getPricing() != null);
        assertThat(models).allMatch(m -> m.getPricing().getInputPricePerMillionTokens() != null);
        assertThat(models).allMatch(m -> m.getPricing().getOutputPricePerMillionTokens() != null);
    }

    @Test
    void should_filter_by_type() {
        AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder().build();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .types(Set.of(ModelType.CHAT))
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getType() == ModelType.CHAT);
    }
}
