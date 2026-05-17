package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.catalog.ModelDescription;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiModelCatalogIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void test_list_models() {
        GoogleGenAiModelCatalog catalog = GoogleGenAiModelCatalog.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .build();

        List<ModelDescription> models = catalog.listModels();
        assertThat(models).isNotEmpty();

        boolean foundGeminiFlash = models.stream().anyMatch(m -> m.name().contains("gemini-2.5-flash"));
        assertThat(foundGeminiFlash).isTrue();
    }
}
