package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.openai.models.ChatModel;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class InternalOpenAiOfficialTestHelper {

    private static final Logger log = LoggerFactory.getLogger(InternalOpenAiOfficialTestHelper.class);

    public static final ChatModel MODEL_NAME = ChatModel.GPT_4O_MINI;

    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL;
    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA;
    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;
    static final OpenAiOfficialStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL;
    static final OpenAiOfficialStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;

    static final OpenAiOfficialChatModel OPEN_AI_CHAT_MODEL;
    static final OpenAiOfficialChatModel OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;
    static final OpenAiOfficialStreamingChatModel OPEN_AI_STREAMING_CHAT_MODEL;

    static {
        // Set up Azure OpenAI models if the environment variables are set
        if (System.getenv("AZURE_OPENAI_ENDPOINT") != null || System.getenv("AZURE_OPENAI_KEY") != null) {
            AZURE_OPEN_AI_CHAT_MODEL = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(MODEL_NAME)
                    .build();

            AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

            AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

            AZURE_OPEN_AI_STREAMING_CHAT_MODEL = OpenAiOfficialStreamingChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(MODEL_NAME)
                    .build();

            AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = OpenAiOfficialStreamingChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

        } else {
            AZURE_OPEN_AI_CHAT_MODEL = null;
            AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA = null;
            AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = null;
            AZURE_OPEN_AI_STREAMING_CHAT_MODEL = null;
            AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = null;
        }

        // Set up OpenAI models if the environment variables are set
        if (System.getenv("OPENAI_API_KEY") != null) {
            OPEN_AI_CHAT_MODEL = OpenAiOfficialChatModel.builder()
                    .apiKey(System.getenv("OPENAI_KEY"))
                    .modelName(MODEL_NAME)
                    .build();

            OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
                    .apiKey(System.getenv("OPENAI_KEY"))
                    .modelName(MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

            OPEN_AI_STREAMING_CHAT_MODEL = OpenAiOfficialStreamingChatModel.builder()
                    .apiKey(System.getenv("OPENAI_KEY"))
                    .modelName(MODEL_NAME)
                    .build();
        } else {
            OPEN_AI_CHAT_MODEL = null;
            OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = null;
            OPEN_AI_STREAMING_CHAT_MODEL = null;
        }
    }

    static List<ChatLanguageModel> modelsNormalAndJsonStrict() {
        List<ChatLanguageModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_CHAT_MODEL != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL);
        }
        if (AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA);
        }
        if (OPEN_AI_CHAT_MODEL != null) {
            models.add(OPEN_AI_CHAT_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing normal model & JSON strict model: skipping tests are Azure OpenAI and OpenAI API keys are not set");
        }
        return models;
    }

    static List<ChatLanguageModel> modelsAllJson() {
        List<ChatLanguageModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA);
        }
        if (AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA);
        }
        if (OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA != null) {
            models.add(OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA);
        }
        if (models.isEmpty()) {
            log.error("Testing strict and non-strict JSON models: skipping tests are Azure OpenAI and OpenAI API keys are not set");
        }
        return models;
    }

    static List<StreamingChatLanguageModel> modelsStreamingNormalAndJsonStrict() {
        List<StreamingChatLanguageModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_STREAMING_CHAT_MODEL != null) {
            models.add(AZURE_OPEN_AI_STREAMING_CHAT_MODEL);
        }
        if (AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA != null) {
            models.add(AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA);
        }
        if (OPEN_AI_STREAMING_CHAT_MODEL != null) {
            models.add(OPEN_AI_STREAMING_CHAT_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing streaming models: skipping tests are Azure OpenAI and OpenAI API keys are not set");
        }
        return models;
    }
}
