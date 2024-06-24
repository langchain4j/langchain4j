package dev.langchain4j.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.ExperimentalTools;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AiServicesWithOllamaToolsIT {

    @DisplayName("Llama3 no tools mode")
    @Nested
    class Llama3NoTools extends AiServicesWithOllamaToolsBaseIT {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName("llama3")
                .temperature(0.0)
                .numCtx(2048)
                .numPredict(2048)
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofMinutes(5))
                .build();

        ChatLanguageModel model() {
            return model;
        }

        @Test
        void should_throw_an_exception() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(model())
                    .tools(new TransactionService())
                    .build();
            String userMessage = "What is the transaction T01 ?";
            assertThatThrownBy(() -> assistant.chat(userMessage))
                    .hasRootCauseExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Tools are currently not supported by this model");
        }
    }

    @DisplayName("Llama3 sequential tools mode")
    @Nested
    class Llama3Sequential extends AiServicesWithOllamaToolsSequentialIT {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName("llama3")
                .temperature(0.0)
                .numCtx(2048)
                .numPredict(2048)
                .logRequests(true)
                .logResponses(true)
                .experimentalTools(ExperimentalTools.SEQUENTIAL)
                .timeout(Duration.ofMinutes(5))
                .build();

        ChatLanguageModel model() {
            return model;
        }
    }

    @DisplayName("Llama3 parallel tool mode")
    @Nested
    class Llama3Parallel extends AiServicesWithOllamaToolsParallelIT {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName("llama3")
                .temperature(0.0)
                .numCtx(2048)
                .numPredict(2048)
                .logRequests(true)
                .logResponses(true)
                .experimentalTools(ExperimentalTools.PARALLEL)
                .timeout(Duration.ofMinutes(5))
                .build();

        ChatLanguageModel model() {
            return model;
        }

    }

    @Disabled("Do not handle complex case yet!")
    @Nested
    @DisplayName("Mistral parallel tool mode")
    class MistralParallel extends AiServicesWithOllamaToolsParallelIT {
        ChatLanguageModel model=  OllamaChatModel.builder()
                    .baseUrl(ollamaUrl)
                    .modelName("mistral")
                    .temperature(0.0)
                    .numCtx(2048)
                    .numPredict(2048)
                    .logRequests(true)
                    .logResponses(true)
                    .experimentalTools(ExperimentalTools.SEQUENTIAL)
                    .timeout(Duration.ofMinutes(5))
                .build();

        ChatLanguageModel model() {
            return model;
        }

    }

    @Disabled("Need a Ollama server with Qwen2 model and Do not handle complex case yet!")
    @Nested
    @DisplayName("Qwen2 sequential tool mode")
    class Qwen2 extends AiServicesWithOllamaToolsSequentialIT {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName("qwen2")
                .temperature(0.0)
                .numCtx(2048)
                .numPredict(2048)
                .logRequests(true)
                .logResponses(true)
                .experimentalTools(ExperimentalTools.SEQUENTIAL)
                .timeout(Duration.ofMinutes(5))
                .build();

        ChatLanguageModel model() {
            return model;
        }
    }

    @Disabled("Need a Ollama server with Qwen2 model and Do not handle complex case yet!")
    @Nested
    @DisplayName("Qwen2 parallel tool mode")
    class Qwen2Parallel extends AiServicesWithOllamaToolsParallelIT {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaUrl)
                .modelName("qwen2")
                .temperature(0.0)
                .numCtx(2048)
                .numPredict(2048)
                .logRequests(true)
                .logResponses(true)
                .experimentalTools(ExperimentalTools.PARALLEL)
                .timeout(Duration.ofMinutes(5))
                .build();

        ChatLanguageModel model() {
            return model;
        }
    }
}
