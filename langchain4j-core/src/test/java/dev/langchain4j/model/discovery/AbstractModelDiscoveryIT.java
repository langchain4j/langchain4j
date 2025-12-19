package dev.langchain4j.model.discovery;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.ModelProvider;

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
        assertThat(models).allMatch(m -> m.name() != null, "All models should have a name");
        assertThat(models).allMatch(m -> m.displayName() != null, "All models should have a display name");
        assertThat(models)
                .allMatch(m -> m.provider() == expectedProvider(), "All models should have the correct provider");
    }

    @Test
    void should_return_correct_provider() {
        ModelDiscovery discovery = createModelDiscovery();

        assertThat(discovery.provider()).isEqualTo(expectedProvider());
    }

}
