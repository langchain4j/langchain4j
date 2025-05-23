package dev.langchain4j.agentic;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollama;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;

public class Models {

    private static final String OLLAMA_BASE_URL = ollamaBaseUrl(ollama);
//    private static final String OLLAMA_BASE_URL = "http://127.0.0.1:11434";

    static final ChatModel BASE_MODEL = OllamaChatModel.builder()
            .baseUrl(OLLAMA_BASE_URL)
            .modelName("qwen2.5:7b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    static final ChatModel PLANNER_MODEL = OllamaChatModel.builder()
            .baseUrl(OLLAMA_BASE_URL)
            .modelName("qwen3:8b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();
}
