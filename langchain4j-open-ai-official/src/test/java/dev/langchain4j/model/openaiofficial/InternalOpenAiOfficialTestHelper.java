package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.openai.models.ChatModel;
import com.openai.models.ImageGenerateParams;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.image.ImageModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for testing OpenAI models.
 * <p>
 * Tests will run depending on the available environment variables:
 * - AZURE_OPENAI_ENDPOINT and AZURE_OPENAI_KEY: Azure OpenAI models will be tested
 * - OPENAI_API_KEY: OpenAI models will be tested
 * - GITHUB_TOKEN: GitHub models will be tested
 */
public class InternalOpenAiOfficialTestHelper {

    private static final Logger log = LoggerFactory.getLogger(InternalOpenAiOfficialTestHelper.class);

    public static final ChatModel CHAT_MODEL_NAME = ChatModel.GPT_4O_MINI;
    public static final com.openai.models.EmbeddingModel EMBEDDING_MODEL_NAME =
            com.openai.models.EmbeddingModel.TEXT_EMBEDDING_3_SMALL;
    public static final com.openai.models.ImageModel IMAGE_MODEL_NAME = com.openai.models.ImageModel.DALL_E_3;

    // Chat models
    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL;
    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS;
    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA;
    static final OpenAiOfficialChatModel AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;
    static final OpenAiOfficialStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL;
    static final OpenAiOfficialStreamingChatModel AZURE_OPEN_AI_CHAT_MODEL_STREAMING_WITH_STRICT_TOOLS;
    static final OpenAiOfficialStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;

    static final OpenAiOfficialChatModel OPEN_AI_CHAT_MODEL;
    static final OpenAiOfficialChatModel OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA;
    static final OpenAiOfficialStreamingChatModel OPEN_AI_STREAMING_CHAT_MODEL;

    static final OpenAiOfficialChatModel GITHUB_MODELS_CHAT_MODEL;

    // Embedding models
    static final OpenAiOfficialEmbeddingModel AZURE_OPEN_AI_EMBEDDING_MODEL;

    static final OpenAiOfficialEmbeddingModel OPEN_AI_EMBEDDING_MODEL;

    // Image models
    static final OpenAiOfficialImageModel AZURE_OPEN_AI_IMAGE_MODEL;
    static final OpenAiOfficialImageModel AZURE_OPEN_AI_IMAGE_MODEL_BASE64;

    static final OpenAiOfficialImageModel OPEN_AI_IMAGE_MODEL;

    static {
        // Set up Azure OpenAI models if the environment variables are set
        if (System.getenv("AZURE_OPENAI_ENDPOINT") != null || System.getenv("AZURE_OPENAI_KEY") != null) {
            AZURE_OPEN_AI_CHAT_MODEL = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .build();

            AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .strictTools(true)
                    .build();

            AZURE_OPEN_AI_CHAT_MODEL_JSON_WITHOUT_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

            AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

            AZURE_OPEN_AI_STREAMING_CHAT_MODEL = OpenAiOfficialStreamingChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .build();

            AZURE_OPEN_AI_CHAT_MODEL_STREAMING_WITH_STRICT_TOOLS = OpenAiOfficialStreamingChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .strictTools(true)
                    .build();

            AZURE_OPEN_AI_STREAMING_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = OpenAiOfficialStreamingChatModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

            AZURE_OPEN_AI_EMBEDDING_MODEL = OpenAiOfficialEmbeddingModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .modelName(EMBEDDING_MODEL_NAME)
                    .build();

            AZURE_OPEN_AI_IMAGE_MODEL = OpenAiOfficialImageModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .size(ImageGenerateParams.Size._1024X1024)
                    .modelName(IMAGE_MODEL_NAME)
                    .build();

            AZURE_OPEN_AI_IMAGE_MODEL_BASE64 = OpenAiOfficialImageModel.builder()
                    .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
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

        // Set up OpenAI models if the environment variables are set
        if (System.getenv("OPENAI_API_KEY") != null) {
            OPEN_AI_CHAT_MODEL = OpenAiOfficialChatModel.builder()
                    .apiKey(System.getenv("OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .build();

            OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
                    .apiKey(System.getenv("OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .build();

            OPEN_AI_STREAMING_CHAT_MODEL = OpenAiOfficialStreamingChatModel.builder()
                    .apiKey(System.getenv("OPENAI_KEY"))
                    .modelName(CHAT_MODEL_NAME)
                    .build();

            OPEN_AI_EMBEDDING_MODEL = OpenAiOfficialEmbeddingModel.builder()
                    .apiKey(System.getenv("OPENAI_KEY"))
                    .modelName(EMBEDDING_MODEL_NAME)
                    .build();

            OPEN_AI_IMAGE_MODEL = OpenAiOfficialImageModel.builder()
                    .apiKey(System.getenv("OPENAI_KEY"))
                    .modelName(IMAGE_MODEL_NAME)
                    .build();

        } else {
            OPEN_AI_CHAT_MODEL = null;
            OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA = null;
            OPEN_AI_STREAMING_CHAT_MODEL = null;
            OPEN_AI_EMBEDDING_MODEL = null;
            OPEN_AI_IMAGE_MODEL = null;
        }

        if (System.getenv("GITHUB_TOKEN") != null) {
            GITHUB_MODELS_CHAT_MODEL = OpenAiOfficialChatModel.builder()
                    .isGitHubModels(true)
                    .modelName(CHAT_MODEL_NAME)
                    .build();
        } else {
            GITHUB_MODELS_CHAT_MODEL = null;
        }
    }

    static List<ChatLanguageModel> chatModelsNormalAndJsonStrict() {
        List<ChatLanguageModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_CHAT_MODEL != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL);
        }
        if (AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS);
        }
        if (AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_JSON_WITH_STRICT_SCHEMA);
        }
        if (OPEN_AI_CHAT_MODEL != null) {
            models.add(OPEN_AI_CHAT_MODEL);
        }
        if (GITHUB_MODELS_CHAT_MODEL != null) {
            models.add(GITHUB_MODELS_CHAT_MODEL);
        }
        if (models.isEmpty()) {
            log.error(
                    "Testing normal model & JSON strict model: skipping tests are Azure OpenAI and OpenAI API keys are not set");
        }
        return models;
    }

    static List<ChatLanguageModel> chatModelsNormalAndStrictTools() {
        List<ChatLanguageModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_CHAT_MODEL != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL);
        }
        if (AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS != null) {
            models.add(AZURE_OPEN_AI_CHAT_MODEL_WITH_STRICT_TOOLS);
        }
        if (OPEN_AI_CHAT_MODEL != null) {
            models.add(OPEN_AI_CHAT_MODEL);
        }
        if (models.isEmpty()) {
            log.error(
                    "Testing normal model & JSON strict tools: skipping tests are Azure OpenAI and OpenAI API keys are not set");
        }
        return models;
    }

    static List<ChatLanguageModel> chatModelsWithJsonResponse() {
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
            log.error("Testing JSON responses: skipping tests are Azure OpenAI and OpenAI API keys are not set");
        }
        return models;
    }

    static List<StreamingChatLanguageModel> chatModelsStreamingNormalAndJsonStrict() {
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

    static List<dev.langchain4j.model.embedding.EmbeddingModel> embeddingModels() {
        List<dev.langchain4j.model.embedding.EmbeddingModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_EMBEDDING_MODEL != null) {
            models.add(AZURE_OPEN_AI_EMBEDDING_MODEL);
        }
        if (OPEN_AI_EMBEDDING_MODEL != null) {
            models.add(OPEN_AI_EMBEDDING_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing embedding models: skipping tests are Azure OpenAI and OpenAI API keys are not set");
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
        if (OPEN_AI_IMAGE_MODEL != null) {
            models.add(OPEN_AI_IMAGE_MODEL);
        }
        if (models.isEmpty()) {
            log.error("Testing image models: skipping tests are Azure OpenAI and OpenAI API keys are not set");
        }
        return models;
    }

    static List<ImageModel> imageModelsBase64() {
        List<ImageModel> models = new ArrayList<>();
        if (AZURE_OPEN_AI_IMAGE_MODEL_BASE64 != null) {
            models.add(AZURE_OPEN_AI_IMAGE_MODEL_BASE64);
        }
        if (models.isEmpty()) {
            log.error("Testing image models base64: skipping tests are Azure OpenAI is not set");
        }
        return models;
    }
}
