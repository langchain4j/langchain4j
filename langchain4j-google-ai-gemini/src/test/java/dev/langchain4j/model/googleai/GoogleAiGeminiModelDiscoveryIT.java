package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiModelDiscoveryIT {

    private static final String API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void should_discover_gemini_models() {
        GoogleAiGeminiModelDiscovery discovery =
                GoogleAiGeminiModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null);
        assertThat(models).allMatch(m -> m.displayName() != null);
        assertThat(models).allMatch(m -> m.provider() == ModelProvider.GOOGLE_AI_GEMINI);
    }

    @Test
    void should_return_google_provider() {
        GoogleAiGeminiModelDiscovery discovery =
                GoogleAiGeminiModelDiscovery.builder().apiKey(API_KEY).build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.GOOGLE_AI_GEMINI);
    }

    @Test
    void should_have_context_window_information() {
        GoogleAiGeminiModelDiscovery discovery =
                GoogleAiGeminiModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.contextWindow() != null && m.contextWindow() > 0);
    }

    @Test
    void should_have_max_output_tokens() {
        GoogleAiGeminiModelDiscovery discovery =
                GoogleAiGeminiModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.maxOutputTokens() != null && m.maxOutputTokens() > 0);
    }

}
