package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.AbstractModelCatalogIT;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicModelCatalogIT extends AbstractModelCatalogIT {

    private static final String API_KEY = System.getenv("ANTHROPIC_API_KEY");

    @Override
    protected ModelProvider expectedProvider() {
    	return ModelProvider.ANTHROPIC;
    }
    
    @Override
    protected ModelCatalog createModelCatalog() {
    	return AnthropicModelCatalog.builder().apiKey(API_KEY).build();
    }
    
    @Test
    void should_discover_anthropic_models() {
        ModelCatalog catalog = createModelCatalog();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null);
        assertThat(models).allMatch(m -> m.displayName() != null);
        assertThat(models).allMatch(m -> m.provider() == ModelProvider.ANTHROPIC);
    }

    @Test
    void should_have_display_name() {
        ModelCatalog catalog = createModelCatalog();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.displayName() != null);
    }

    @Test
    void should_have_creation_timestamp() {
        ModelCatalog catalog = createModelCatalog();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.createdAt() != null);
    }
}
