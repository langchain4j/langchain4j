package dev.langchain4j.agentic;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import java.time.Duration;

public class Models {

    private enum MODEL_PROVIDER {
        OPENAI,
        OLLAMA
    }

    private static final MODEL_PROVIDER modelProvider = Models.MODEL_PROVIDER.OPENAI;

    private static final String OLLAMA_DEFAULT_URL = "http://127.0.0.1:11434";
    private static final String OLLAMA_ENV_URL = System.getenv("OLLAMA_BASE_URL");

    private static final String OLLAMA_BASE_URL = OLLAMA_ENV_URL != null ? OLLAMA_ENV_URL : OLLAMA_DEFAULT_URL;

    private static final ChatModel OPENAI_BASE_MODEL = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(OpenAiChatModelName.GPT_4_O_MINI)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build();

    private static final ChatModel OPENAI_PLANNER_MODEL = OPENAI_BASE_MODEL;

    private static final ChatModel OLLAMA_BASE_MODEL = OllamaChatModel.builder()
            .baseUrl(OLLAMA_BASE_URL)
            .modelName("qwen2.5:7b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    private static final ChatModel OLLAMA_PLANNER_MODEL = OllamaChatModel.builder()
            .baseUrl(OLLAMA_BASE_URL)
            .modelName("qwen3:8b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .think(false)
            .build();

    public static ChatModel baseModel() {
        return switch (modelProvider) {
            case OPENAI -> OPENAI_BASE_MODEL;
            case OLLAMA -> OLLAMA_BASE_MODEL;
        };
    }

    public static ChatModel plannerModel() {
        return switch (modelProvider) {
            case OPENAI -> OPENAI_PLANNER_MODEL;
            case OLLAMA -> OLLAMA_PLANNER_MODEL;
        };
    }
}
