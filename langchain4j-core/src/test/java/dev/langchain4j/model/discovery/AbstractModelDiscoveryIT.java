package dev.langchain4j.model.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Abstract base class for {@link ModelDiscovery} integration tests.
 * Provider-specific implementations should extend this class and implement
 * {@link #createModelDiscovery()}.
 */
public abstract class AbstractModelDiscoveryIT {

    /**
     * Create an instance of the ModelDiscovery implementation to test.
     * This should be properly configured with credentials, URLs, etc.
     *
     * @return Configured ModelDiscovery instance
     */
    protected abstract ModelDiscovery createModelDiscovery();

    /**
     * Returns the expected provider for this discovery implementation.
     *
     * @return Expected ModelProvider
     */
    protected abstract ModelProvider expectedProvider();

    @Test
    void should_discover_models() {
        ModelDiscovery discovery = createModelDiscovery();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getId() != null, "All models should have an ID");
        assertThat(models).allMatch(m -> m.getName() != null, "All models should have a name");
        assertThat(models)
                .allMatch(m -> m.getProvider() == expectedProvider(), "All models should have the correct provider");
    }

    @Test
    void should_return_correct_provider() {
        ModelDiscovery discovery = createModelDiscovery();

        assertThat(discovery.provider()).isEqualTo(expectedProvider());
    }

    @Test
    void should_discover_models_with_null_filter() {
        ModelDiscovery discovery = createModelDiscovery();

        List<ModelDescription> modelsWithoutFilter = discovery.discoverModels();
        List<ModelDescription> modelsWithNullFilter = discovery.discoverModels(null);

        assertThat(modelsWithNullFilter).hasSameSizeAs(modelsWithoutFilter);
    }

    @Test
    void should_discover_models_with_empty_filter() {
        ModelDiscovery discovery = createModelDiscovery();

        List<ModelDescription> modelsWithoutFilter = discovery.discoverModels();
        List<ModelDescription> modelsWithEmptyFilter = discovery.discoverModels(ModelDiscoveryFilter.ALL);

        assertThat(modelsWithEmptyFilter).hasSameSizeAs(modelsWithoutFilter);
    }

    @Test
    void should_filter_by_type_if_supported() {
        ModelDiscovery discovery = createModelDiscovery();

        ModelDiscoveryFilter filter =
                ModelDiscoveryFilter.builder().types(Set.of(ModelType.CHAT)).build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        // If filtering is supported, all models should be CHAT type
        // If not supported, we may get all models or client-side filtered results
        if (discovery.supportsFiltering()) {
            assertThat(models)
                    .allMatch(
                            m -> m.getType() == ModelType.CHAT,
                            "All models should be CHAT type when filtering is supported");
        } else {
            // For providers without server-side filtering,
            // implementation may choose to filter client-side or ignore filter
            assertThat(models).isNotNull();
        }
    }

    @Test
    void should_filter_by_context_window() {
        ModelDiscovery discovery = createModelDiscovery();

        ModelDiscoveryFilter filter =
                ModelDiscoveryFilter.builder().minContextWindow(100000).build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        // For models with context window information, verify filtering
        models.stream().filter(m -> m.getContextWindow() != null).forEach(m -> assertThat(m.getContextWindow())
                .isGreaterThanOrEqualTo(100000));
    }

    @Test
    void should_handle_deprecated_filter() {
        ModelDiscovery discovery = createModelDiscovery();

        ModelDiscoveryFilter includeDeprecated =
                ModelDiscoveryFilter.builder().includeDeprecated(true).build();

        ModelDiscoveryFilter excludeDeprecated =
                ModelDiscoveryFilter.builder().includeDeprecated(false).build();

        List<ModelDescription> withDeprecated = discovery.discoverModels(includeDeprecated);
        List<ModelDescription> withoutDeprecated = discovery.discoverModels(excludeDeprecated);

        // Number of models excluding deprecated should be <= models including deprecated
        assertThat(withoutDeprecated.size()).isLessThanOrEqualTo(withDeprecated.size());

        // Verify no deprecated models when excluded
        assertThat(withoutDeprecated).noneMatch(m -> Boolean.TRUE.equals(m.isDeprecated()));
    }
}
