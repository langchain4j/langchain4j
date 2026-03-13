package dev.langchain4j.model.info.util;

import dev.langchain4j.model.info.ModelInfo;
import dev.langchain4j.model.info.Provider;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ModelRegistryTest implements WithAssertions {

    private static final String JSON =
            """
			{
			  "openai": {
			    "name": "OpenAI",
			    "models": {
			      "gpt-4": {
			        "id": "gpt-4",
			        "name": "GPT-4",
			        "family": "gpt",
			        "reasoning": true,
			        "tool_call": true,
			        "open_weights": true
			      }
			    }
			  },
			  "google": {
			    "name": "Google",
			    "models": {
			      "gemini-pro": {
			        "id": "gemini-pro",
			        "name": "Gemini Pro",
			        "family": "gemini",
			        "reasoning": false,
			        "tool_call": false,
			        "open_weights": true
			      }
			    }
			  }
			}
			""";

    @Test
    void load_from_json() throws IOException {

        ModelRegistry registry = ModelRegistry.fromJson(JSON);

        assertThat(registry.getProviderCount()).isEqualTo(2);
        assertThat(registry.getProviderIds()).containsExactly("openai", "google");
        assertThat(registry.getTotalModelCount()).isEqualTo(2);
    }

    @Test
    void provider_lookup() throws IOException {

        ModelRegistry registry = ModelRegistry.fromJson(JSON);

        Provider provider = registry.getProvider("openai");

        assertThat(provider).isNotNull();
        assertThat(provider.getId()).isEqualTo("openai");
    }

    @Test
    void model_lookup() throws IOException {

        ModelRegistry registry = ModelRegistry.fromJson(JSON);

        ModelInfo model = registry.getModelInfo("openai", "gpt-4");

        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo("GPT-4");
        assertThat(model.supportsReasoning()).isTrue();
        assertThat(model.supportsToolCalls()).isTrue();
    }

    @Test
    void get_all_models() throws IOException {

        ModelRegistry registry = ModelRegistry.fromJson(JSON);

        List<ModelInfo> models = registry.getAllModelsInfo();

        assertThat(models).hasSize(2);
    }

    @Test
    void search_by_name() throws IOException {

        ModelRegistry registry = ModelRegistry.fromJson(JSON);

        List<ModelInfo> result = registry.searchByName("gpt");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("gpt-4");
    }

    @Test
    void get_models_by_family() throws IOException {

        ModelRegistry registry = ModelRegistry.fromJson(JSON);

        List<ModelInfo> models = registry.getModelsByFamily("gemini");

        assertThat(models).hasSize(1);
        assertThat(models.get(0).getId()).isEqualTo("gemini-pro");
    }

    @Test
    void reasoning_models() throws IOException {

        ModelRegistry registry = ModelRegistry.fromJson(JSON);

        List<ModelInfo> models = registry.getReasoningModels();

        assertThat(models).hasSize(1);
        assertThat(models.get(0).getId()).isEqualTo("gpt-4");
    }

    @Test
    void statistics() throws IOException {

        ModelRegistry registry = ModelRegistry.fromJson(JSON);

        Map<String, Long> countByProvider = registry.getModelCountByProvider();

        assertThat(countByProvider).containsEntry("openai", 1L).containsEntry("google", 1L);
    }

    @Test
    void to_string() throws IOException {

        ModelRegistry registry = ModelRegistry.fromJson(JSON);

        assertThat(registry.toString()).contains("providers=2").contains("totalModels=2");
    }
}
