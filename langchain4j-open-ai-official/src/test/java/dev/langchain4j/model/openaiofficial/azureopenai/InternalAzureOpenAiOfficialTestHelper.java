package dev.langchain4j.model.openaiofficial.azureopenai;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.openai.models.images.ImageGenerateParams;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
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
 * Helper class for testing Azure OpenAI models.
 * <p>
 * Tests will run depending on the available environment variables:
 * - AZURE_OPENAI_ENDPOINT and AZURE_OPENAI_KEY: Azure OpenAI models will be tested
 * <p>
 */
public class InternalAzureOpenAiOfficialTestHelper {

    private static final Logger log = LoggerFactory.getLogger(InternalAzureOpenAiOfficialTestHelper.class);

    public static final com.openai.models.ChatModel CHAT_MODEL_NAME = com.openai.models.ChatModel.GPT_4O_MINI;
    public static final com.openai.models.ChatModel CHAT_MODEL_NAME_ALTERNATE = com.openai.models.ChatModel.GPT_4O;
    public static final com.openai.models.embeddings.EmbeddingModel EMBEDDING_MODEL_NAME =
            com.openai.models.embeddings.EmbeddingModel.TEXT_EMBEDDING_3_SMALL;
    public static final com.openai.models.images.ImageModel IMAGE_MODEL_NAME =
            com.openai.models.images.ImageModel.DALL_E_3;

    // Chat models
    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL;
    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS;
    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA;
    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;
    static final OpenAiOfficialStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL;
    static final OpenAiOfficialStreamingChatModel AZURE_OPEN_AI_CHAT_MODEL_STREAMING_WITH_STRICT_TOOLS;
    static final OpenAiOfficialStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;

    // Embedding models
    static final OpenAiOfficialEmbeddingModel AZURE_OPEN_AI_EMBEDDING_MODEL;

    // Image models
    static final OpenAiOfficialImageModel AZURE_OPEN_AI_IMAGE_MODEL;
    static final OpenAiOfficialImageModel AZURE_OPEN_AI_IMAGE_MODEL_BASE64;

    static {
        // Set up Azure OpenAI models if the environment variables are set
        if (System.getenv("AZURE_OPENAI_ENDPOINT") != null || System.getenv("AZURE_OPENAI_KEY") != null) {
            AZURE_OPEN_AI_CHAT_MODEL = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .build();

            AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .strictTools(true)
                    .build();

            AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(false)
                    .build();

            AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

            AZURE_OPEN_AI_STREAMING_CHAT_MODEL = OpenAiOfficialStreamingChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .build();

            AZURE_OPEN_AI_CHAT_MODEL_STREAMING_WITH_STRICT_TOOLS = OpenAiOfficialStreamingChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .strictTools(true)
                    .build();

            AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = OpenAiOfficialStreamingChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

            AZURE_OPEN_AI_EMBEDDING_MODEL = OpenAiOfficialEmbeddingModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(EMBEDDING_MODEL_NAME)
                    .build();

            AZURE_OPEN_AI_IMAGE_MODEL = OpenAiOfficialImageModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .size(ImageGenerateParams.Size._1024X1024)
                    .modelName(IMAGE_MODEL_NAME)
                    .build();

            AZURE_OPEN_AI_IMAGE_MODEL_BASE64 = OpenAiOfficialImageModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(IMAGE_MODEL_NAME)
                    .responseFormat(ImageGenerateParams.ResponseFormat.B64_JSON)
                    .build();

        } else {
            AZURE_OPEN_AI_CHAT_MODEL = null;
            AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS = null;
            AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA = null;
            AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = null;
            AZURE_OPEN_AI_STREAMING_CHAT_MODEL = null;
            AZURE_OPEN_AI_CHAT_MODEL_STREAMING_WITH_STRICT_TOOLS = null;
            AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = null;
            AZURE_OPEN_AI_EMBEDDING_MODEL = null;
            AZURE_OPEN_AI_IMAGE_MODEL = null;
            AZURE_OPEN_AI_IMAGE_MODEL_BASE64 = null;
        }
    }

    static List<ChatModel> chatModelsNormalAndJsonStrict() {
        List<ChatModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_CHAT_MODEL != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL);
        }
        if (AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS);
        }
        if (AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA);
        }
        if (models.isEmpty()) {
            log.error("Testing normal model & JSON strict model: skipping tests as Azure OpenAI keys are not set");
        }
        return models;
    }

    static List<ChatModel> chatModelsNormalAndStrictTools() {
        List<ChatModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_CHAT_MODEL != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL);
        }
        if (AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS);
        }
        if (models.isEmpty()) {
            log.error("Testing normal model & JSON strict tools: skipping tests as Azure OpenAI keys are not set");
        }
        return models;
    }

    static List<ChatModel> chatModelsWithJsonResponse() {
        List<ChatModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA);
        }
        if (AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA);
        }
        if (models.isEmpty()) {
            log.error("Testing JSON responses: skipping tests as Azure OpenAI keys are not set");
        }
        return models;
    }

    static List<StreamingChatModel> chatModelsStreamingNormalAndJsonStrict() {
        List<StreamingChatModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_STREAMING_CHAT_MODEL != null) {
            models.add(AZURE_OPEN_AI_STREAMING_CHAT_MODEL);
        }
        if (AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA != null) {
            models.add(AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA);
        }
        if (models.isEmpty()) {
            log.error("Testing streaming models: skipping tests as Azure OpenAI keys are not set");
        }
        return models;
    }

    static List<dev.langchain4j.model.embedding.EmbeddingModel> embeddingModels() {
        List<dev.langchain4j.model.embedding.EmbeddingModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_EMBEDDING_MODEL != null) {
            models.add(AZURE_OPEN_AI_EMBEDDING_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing embedding models: skipping tests as Azure OpenAI keys are not set");
        }
        return models;
    }

    static List<ImageModel> imageModelsUrl() {
        List<ImageModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_IMAGE_MODEL != null) {
            models.add(AZURE_OPEN_AI_IMAGE_MODEL);
        }
        if (AZURE_OPEN_AI_IMAGE_MODEL_BASE64 != null) {
            models.add(AZURE_OPEN_AI_IMAGE_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing image models: skipping tests as Azure OpenAI keys are not set");
        }
        return models;
    }

    static List<ImageModel> imageModelsBase64() {
        List<ImageModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_IMAGE_MODEL_BASE64 != null) {
            models.add(AZURE_OPEN_AI_IMAGE_MODEL_BASE64);
        }
        if (models.isEmpty()) {
            log.error("Testing image models base64: skipping tests as Azure OpenAI keys are not set");
        }
        return models;
    }
}
