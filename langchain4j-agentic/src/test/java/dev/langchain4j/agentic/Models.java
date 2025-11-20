package dev.langchain4j.agentic;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;

public class Models {

    private enum MODEL_PROVIDER {
        OPENAI,
        GEMINI,
        OLLAMA
    }

    private static final MODEL_PROVIDER modelProvider = MODEL_PROVIDER.OPENAI;

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

    private static final StreamingChatModel OPENAI_STREAMING_BASE_MODEL = OpenAiStreamingChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(OpenAiChatModelName.GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
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

    private static final ChatModel GEMINI_BASE_MODEL = GoogleAiGeminiChatModel.builder()
            .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
            .modelName("gemini-2.5-flash-lite")
            .logRequests(true)
            .logResponses(true)
            .build();

    private static final StreamingChatModel GEMINI_STREAMING_BASE_MODEL = GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
            .modelName("gemini-2.5-flash-lite")
            .logRequests(true)
            .logResponses(true)
            .build();

    private static final ChatModel GEMINI_PLANNER_MODEL = GEMINI_BASE_MODEL;

    private static final StreamingChatModel OLLAMA_STREAMING_BASE_MODEL = OllamaStreamingChatModel.builder()
            .baseUrl(OLLAMA_BASE_URL)
            .modelName("qwen3:8b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .think(false)
            .build();

    public static ChatModel baseModel() {
        return switch (modelProvider) {
            case OPENAI -> OPENAI_BASE_MODEL;
            case OLLAMA -> OLLAMA_BASE_MODEL;
            case GEMINI -> GEMINI_BASE_MODEL;
        };
    }

    public static StreamingChatModel streamingBaseModel() {
        return switch (modelProvider) {
            case OPENAI -> OPENAI_STREAMING_BASE_MODEL;
            case OLLAMA -> OLLAMA_STREAMING_BASE_MODEL;
            case GEMINI -> GEMINI_STREAMING_BASE_MODEL;
        };
    }

    public static ChatModel plannerModel() {
        return switch (modelProvider) {
            case OPENAI -> OPENAI_PLANNER_MODEL;
            case OLLAMA -> OLLAMA_PLANNER_MODEL;
            case GEMINI -> GEMINI_PLANNER_MODEL;
        };
    }
}
