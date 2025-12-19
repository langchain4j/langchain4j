package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.*;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelDescription;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiModelCatalogIT {

    private static final String API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void should_discover_gemini_models() {
        GoogleAiGeminiModelCatalog catalog =
                GoogleAiGeminiModelCatalog.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null);
        assertThat(models).allMatch(m -> m.displayName() != null);
        assertThat(models).allMatch(m -> m.provider() == ModelProvider.GOOGLE_AI_GEMINI);
    }

    @Test
    void should_return_google_provider() {
        GoogleAiGeminiModelCatalog catalog =
                GoogleAiGeminiModelCatalog.builder().apiKey(API_KEY).build();

        assertThat(catalog.provider()).isEqualTo(ModelProvider.GOOGLE_AI_GEMINI);
    }

    @Test
    void should_have_context_window_information() {
        GoogleAiGeminiModelCatalog catalog =
                GoogleAiGeminiModelCatalog.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.maxInputTokens() != null && m.maxInputTokens() > 0);
    }

    @Test
    void should_have_max_output_tokens() {
        GoogleAiGeminiModelCatalog catalog =
                GoogleAiGeminiModelCatalog.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.maxOutputTokens() != null && m.maxOutputTokens() > 0);
    }
}
