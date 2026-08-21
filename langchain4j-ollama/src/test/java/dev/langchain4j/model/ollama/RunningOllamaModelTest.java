package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.OllamaJsonUtils.fromJson;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunningOllamaModelTest {

    @Test
    void should_deserialize_context_length_from_running_models_response() {
        String json = """
                {
                  "models": [
                    {
                      "name": "gemma3",
                      "model": "gemma3",
                      "size": 6591830464,
                      "digest": "a2af6cc3eb7fa8be8504abaf9b04e88f17a119ec3f04a3addf55f92841195f5a",
                      "details": {
                        "format": "gguf",
                        "family": "gemma3",
                        "families": [
                          "gemma3"
                        ],
                        "parameter_size": "4.3B",
                        "quantization_level": "Q4_K_M"
                      },
                      "expires_at": "2025-10-17T16:47:07.93355-07:00",
                      "size_vram": 5333539264,
                      "context_length": 4096
                    }
                  ]
                }
                """;

        RunningModelsListResponse response = fromJson(json, RunningModelsListResponse.class);

        assertThat(response.getModels()).singleElement().satisfies(model -> {
            assertThat(model.getName()).isEqualTo("gemma3");
            assertThat(model.getContextLength()).isEqualTo(4096);
        });
    }
}
