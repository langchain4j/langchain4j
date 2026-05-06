package dev.langchain4j.model.anthropic.common;

import static dev.langchain4j.model.ModelProvider.ANTHROPIC;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.anthropic.AnthropicModelCatalog;
import dev.langchain4j.model.catalog.AbstractModelCatalogIT;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicModelCatalogIT extends AbstractModelCatalogIT {

    @Override
    protected ModelCatalog createModelCatalog() {
        return AnthropicModelCatalog.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .build();
    }

    @Override
    protected ModelProvider expectedProvider() {
        return ANTHROPIC;
    }

    @Test
    void should_have_creation_timestamp() {
        ModelCatalog catalog = createModelCatalog();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.createdAt() != null);
    }

    @Test
    void should_have_max_input_tokens() {
        ModelCatalog catalog = createModelCatalog();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.maxInputTokens() != null && m.maxInputTokens() > 0);
    }

    @Test
    void should_have_max_output_tokens() {
        ModelCatalog catalog = createModelCatalog();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.maxOutputTokens() != null && m.maxOutputTokens() > 0);
    }
}
