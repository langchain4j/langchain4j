package dev.langchain4j.model.catalog;

import static org.assertj.core.api.Assertions.*;

import dev.langchain4j.model.ModelProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Abstract base class for {@link ModelCatalog} integration tests.
 * Provider-specific implementations should extend this class and implement
 * {@link #createModelCatalog()} and {@link #expectedProvider()}.
 */
public abstract class AbstractModelCatalogIT {

    /**
     * Create an instance of the ModelCatalog implementation to test.
     * This should be properly configured with credentials, URLs, etc.
     *
     * @return Configured ModelCatalog instance
     */
    protected abstract ModelCatalog createModelCatalog();

    /**
     * Returns the expected provider for this catalog implementation.
     *
     * @return Expected ModelProvider
     */
    protected abstract ModelProvider expectedProvider();

    @Test
    void should_discover_models() {
        ModelCatalog catalog = createModelCatalog();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null, "All models should have a name");
        assertThat(models).allMatch(m -> m.displayName() != null, "All models should have a display name");
        assertThat(models)
                .allMatch(m -> m.provider() == expectedProvider(), "All models should have the correct provider");
    }

    @Test
    void should_return_correct_provider() {
        ModelCatalog catalog = createModelCatalog();

        assertThat(catalog.provider()).isEqualTo(expectedProvider());
    }
}
