package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.openai.models.ChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialEmbeddingModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialImageModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for testing OpenAI models.
 * <p>
 * Tests will run depending on the available environment variables:
 * - OPENAI_API_KEY: OpenAI models will be tested
 * <p>
 */
public class InternalOpenAiOfficialTestHelper {

    private static final Logger log = LoggerFactory.getLogger(InternalOpenAiOfficialTestHelper.class);

    public static final ChatModel CHAT_MODEL_NAME = ChatModel.GPT_4O_MINI;
    public static final ChatModel CHAT_MODEL_NAME_ALTERNATE = ChatModel.GPT_4O;
    public static final com.openai.models.embeddings.EmbeddingModel EMBEDDING_MODEL_NAME =
            com.openai.models.embeddings.EmbeddingModel.TEXT_EMBEDDING_3_SMALL;
    public static final com.openai.models.images.ImageModel IMAGE_MODEL_NAME =
            com.openai.models.images.ImageModel.DALL_E_3;

    // Chat models
    static final OpenAiOfficialChatModel OPEN_AI_CHAT_MODEL;
    static final OpenAiOfficialChatModel OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;
    static final OpenAiOfficialStreamingChatModel OPEN_AI_STREAMING_CHAT_MODEL;

    // Embedding models
    static final OpenAiOfficialEmbeddingModel OPEN_AI_EMBEDDING_MODEL;

    // Image models
    static final OpenAiOfficialImageModel OPEN_AI_IMAGE_MODEL;

    static {
        // Set up OpenAI models if the environment variables are set
        if (System.getenv("OPENAI_API_KEY") != null) {
            OPEN_AI_CHAT_MODEL = OpenAiOfficialChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .build();

            OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

            OPEN_AI_STREAMING_CHAT_MODEL = OpenAiOfficialStreamingChatModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .build();

            OPEN_AI_EMBEDDING_MODEL = OpenAiOfficialEmbeddingModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(EMBEDDING_MODEL_NAME)
                    .build();

            OPEN_AI_IMAGE_MODEL = OpenAiOfficialImageModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(IMAGE_MODEL_NAME)
                    .build();

        } else {
            OPEN_AI_CHAT_MODEL = null;
            OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = null;
            OPEN_AI_STREAMING_CHAT_MODEL = null;
            OPEN_AI_EMBEDDING_MODEL = null;
            OPEN_AI_IMAGE_MODEL = null;
        }
    }

    static List<ChatLanguageModel> chatModelsNormalAndJsonStrict() {
        List<ChatLanguageModel> models = new ArrayList<>();
        if (OPEN_AI_CHAT_MODEL != null) {
            models.add(OPEN_AI_CHAT_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing normal model & JSON strict model: skipping tests as OpenAI API keys are not set");
        }
        return models;
    }

    static List<ChatLanguageModel> chatModelsNormalAndStrictTools() {
        List<ChatLanguageModel> models = new ArrayList<>();
        if (OPEN_AI_CHAT_MODEL != null) {
            models.add(OPEN_AI_CHAT_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing normal model & JSON strict tools: skipping tests as OpenAI API keys are not set");
        }
        return models;
    }

    static List<ChatLanguageModel> chatModelsWithJsonResponse() {
        List<ChatLanguageModel> models = new ArrayList<>();
        if (OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA != null) {
            models.add(OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA);
        }
        if (models.isEmpty()) {
            log.error("Testing JSON responses: skipping tests as OpenAI API keys are not set");
        }
        return models;
    }

    static List<StreamingChatLanguageModel> chatModelsStreamingNormalAndJsonStrict() {
        List<StreamingChatLanguageModel> models = new ArrayList<>();
        if (OPEN_AI_STREAMING_CHAT_MODEL != null) {
            models.add(OPEN_AI_STREAMING_CHAT_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing streaming models: skipping tests as OpenAI API keys are not set");
        }
        return models;
    }

    static List<dev.langchain4j.model.embedding.EmbeddingModel> embeddingModels() {
        List<dev.langchain4j.model.embedding.EmbeddingModel> models = new ArrayList<>();
        if (OPEN_AI_EMBEDDING_MODEL != null) {
            models.add(OPEN_AI_EMBEDDING_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing embedding models: skipping tests as OpenAI API keys are not set");
        }
        return models;
    }

    static List<ImageModel> imageModelsUrl() {
        List<ImageModel> models = new ArrayList<>();
        if (OPEN_AI_IMAGE_MODEL != null) {
            models.add(OPEN_AI_IMAGE_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing image models: skipping tests as OpenAI API keys are not set");
        }
        return models;
    }
}
