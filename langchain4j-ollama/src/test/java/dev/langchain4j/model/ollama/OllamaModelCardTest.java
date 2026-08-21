package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.OllamaJsonUtils.fromJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OllamaModelCardTest {

    @Test
    void should_deserialize_show_model_information_response() {
        String json = """
                {
                  "license": "Apache-2.0",
                  "modelfile": "FROM llama3.2",
                  "parameters": "temperature 0.8",
                  "template": "{{ .Prompt }}",
                  "system": "You are helpful",
                  "details": {
                    "parent_model": "llama3.2",
                    "format": "gguf",
                    "family": "llama",
                    "families": [
                      "llama"
                    ],
                    "parameter_size": "3.2B",
                    "quantization_level": "Q4_K_M"
                  },
                  "messages": [
                    {
                      "role": "SYSTEM",
                      "content": "Use short answers",
                      "thinking": "Internal reasoning",
                      "images": [
                        "base64-image"
                      ],
                      "tool_calls": [
                        {
                          "function": {
                            "index": 0,
                            "name": "get_weather",
                            "arguments": {
                              "city": "Paris"
                            }
                          }
                        },
                        {
                          "function": {
                            "name": "get_time",
                            "arguments": {
                              "city": "Paris"
                            }
                          }
                        }
                      ],
                      "tool_name": "get_weather"
                    }
                  ],
                  "model_info": {
                    "general.architecture": "llama"
                  },
                  "projector_info": {
                    "clip.has_text_encoder": true
                  },
                  "tensors": [
                    {
                      "name": "token_embd.weight",
                      "type": "F16",
                      "shape": [
                        32000,
                        4096
                      ]
                    }
                  ],
                  "capabilities": [
                    "completion",
                    "tools"
                  ],
                  "modified_at": "2025-01-02T03:04:05Z"
                }
                """;

        OllamaModelCard modelCard = fromJson(json, OllamaModelCard.class);

        assertThat(modelCard.getLicense()).isEqualTo("Apache-2.0");
        assertThat(modelCard.getModelfile()).isEqualTo("FROM llama3.2");
        assertThat(modelCard.getParameters()).isEqualTo("temperature 0.8");
        assertThat(modelCard.getTemplate()).isEqualTo("{{ .Prompt }}");
        assertThat(modelCard.getSystem()).isEqualTo("You are helpful");
        assertThat(modelCard.getDetails().getParentModel()).isEqualTo("llama3.2");
        assertThat(modelCard.getModelInfo()).containsEntry("general.architecture", "llama");
        assertThat(modelCard.getProjectorInfo()).containsEntry("clip.has_text_encoder", true);
        assertThat(modelCard.getCapabilities()).containsExactly("completion", "tools");
        assertThat(modelCard.getModifiedAt()).isEqualTo(OffsetDateTime.parse("2025-01-02T03:04:05Z"));

        assertThat(modelCard.getMessages()).singleElement().satisfies(message -> {
            assertThat(message.getRole()).isEqualTo("system");
            assertThat(message.getContent()).isEqualTo("Use short answers");
            assertThat(message.getThinking()).isEqualTo("Internal reasoning");
            assertThat(message.getImages()).containsExactly("base64-image");
            assertThat(message.getToolName()).isEqualTo("get_weather");
            assertThat(message.getToolCalls())
                    .hasSize(2)
                    .satisfiesExactly(
                            toolCall -> {
                                assertThat(toolCall.getFunction().getIndex()).isEqualTo(0);
                                assertThat(toolCall.getFunction().getName()).isEqualTo("get_weather");
                                assertThat(toolCall.getFunction().getArguments())
                                        .containsEntry("city", "Paris");
                            },
                            toolCall -> {
                                assertThat(toolCall.getFunction().getIndex()).isNull();
                                assertThat(toolCall.getFunction().getName()).isEqualTo("get_time");
                                assertThat(toolCall.getFunction().getArguments())
                                        .containsEntry("city", "Paris");
                            });
        });
        assertThat(modelCard.getTensors()).singleElement().satisfies(tensor -> {
            assertThat(tensor.getName()).isEqualTo("token_embd.weight");
            assertThat(tensor.getType()).isEqualTo("F16");
            assertThat(tensor.getShape()).containsExactly(32000L, 4096L);
        });
    }

    @Test
    void should_deserialize_show_model_information_response_without_optional_fields() {
        String json = """
                {
                  "modelfile": "FROM llama3.2",
                  "template": "{{ .Prompt }}",
                  "details": {
                    "format": "gguf"
                  }
                }
                """;

        OllamaModelCard modelCard = fromJson(json, OllamaModelCard.class);

        assertThat(modelCard.getModelfile()).isEqualTo("FROM llama3.2");
        assertThat(modelCard.getTemplate()).isEqualTo("{{ .Prompt }}");
        assertThat(modelCard.getSystem()).isNull();
        assertThat(modelCard.getMessages()).isNull();
        assertThat(modelCard.getProjectorInfo()).isNull();
        assertThat(modelCard.getTensors()).isNull();
        assertThat(modelCard.getDetails().getParentModel()).isNull();
    }

    @Test
    void should_build_with_all_model_card_fields() {
        OffsetDateTime modifiedAt = OffsetDateTime.parse("2025-01-02T03:04:05Z");
        OllamaModelDetails details = OllamaModelDetails.builder()
                .parentModel("llama3.2")
                .format("gguf")
                .build();
        List<OllamaModelMessage> messages = List.of(OllamaModelMessage.builder()
                .role("SYSTEM")
                .content("Use short answers")
                .thinking("Internal reasoning")
                .images(List.of("base64-image"))
                .toolCalls(List.of(OllamaModelToolCall.builder()
                        .function(OllamaModelToolCallFunction.builder()
                                .index(0)
                                .name("get_weather")
                                .arguments(Map.of("city", "Paris"))
                                .build())
                        .build()))
                .toolName("get_weather")
                .build());
        Map<String, Object> modelInfo = Map.of("general.architecture", "llama");
        Map<String, Object> projectorInfo = Map.of("clip.has_text_encoder", true);
        List<OllamaModelTensor> tensors = List.of(OllamaModelTensor.builder()
                .name("token_embd.weight")
                .type("F16")
                .shape(List.of(32000L, 4096L))
                .build());

        OllamaModelCard modelCard = OllamaModelCard.builder()
                .license("Apache-2.0")
                .modelfile("FROM llama3.2")
                .parameters("temperature 0.8")
                .template("{{ .Prompt }}")
                .system("You are helpful")
                .details(details)
                .messages(messages)
                .modelInfo(modelInfo)
                .projectorInfo(projectorInfo)
                .tensors(tensors)
                .capabilities(List.of("completion", "tools"))
                .modifiedAt(modifiedAt)
                .build();

        assertThat(modelCard.getLicense()).isEqualTo("Apache-2.0");
        assertThat(modelCard.getModelfile()).isEqualTo("FROM llama3.2");
        assertThat(modelCard.getParameters()).isEqualTo("temperature 0.8");
        assertThat(modelCard.getTemplate()).isEqualTo("{{ .Prompt }}");
        assertThat(modelCard.getSystem()).isEqualTo("You are helpful");
        assertThat(modelCard.getDetails()).isSameAs(details);
        assertThat(modelCard.getMessages()).singleElement().satisfies(message -> {
            assertThat(message.getRole()).isEqualTo("system");
            assertThat(message.getToolCalls()).singleElement().satisfies(toolCall -> {
                assertThat(toolCall.getFunction().getIndex()).isEqualTo(0);
                assertThat(toolCall.getFunction().getName()).isEqualTo("get_weather");
            });
        });
        assertThat(modelCard.getModelInfo()).isSameAs(modelInfo);
        assertThat(modelCard.getProjectorInfo()).isSameAs(projectorInfo);
        assertThat(modelCard.getTensors()).isSameAs(tensors);
        assertThat(modelCard.getCapabilities()).containsExactly("completion", "tools");
        assertThat(modelCard.getModifiedAt()).isEqualTo(modifiedAt);
    }
}
