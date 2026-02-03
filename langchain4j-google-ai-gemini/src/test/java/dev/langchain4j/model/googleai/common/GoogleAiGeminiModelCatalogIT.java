package dev.langchain4j.model.googleai.common;

import static dev.langchain4j.model.ModelProvider.GOOGLE_AI_GEMINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.AbstractModelCatalogIT;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.googleai.GoogleAiGeminiModelCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiModelCatalogIT extends AbstractModelCatalogIT {

    @Override
    protected ModelCatalog createModelCatalog() {
        return GoogleAiGeminiModelCatalog.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .build();
    }

    @Override
    protected ModelProvider expectedProvider() {
        return GOOGLE_AI_GEMINI;
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
