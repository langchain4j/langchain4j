package dev.langchain4j.model.ollama.common;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_1;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_2;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_2_VISION;
import static dev.langchain4j.model.ollama.OllamaImage.OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.localOllamaImage;
import static dev.langchain4j.model.ollama.OllamaImage.resolve;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaResponsesChatModel;
import dev.langchain4j.model.ollama.OllamaResponsesChatRequestParameters;
import dev.langchain4j.model.output.TokenUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OllamaResponsesChatModelIT extends AbstractChatModelIT {

    /**
     * Using map to avoid restarting the same ollama image.
     */
    private static final Map<String, LC4jOllamaContainer> CONTAINER_MAP = new HashMap<>();

    private static final String MODEL_WITH_TOOLS = LLAMA_3_1;
    private static LC4jOllamaContainer ollamaWithTools;

    private static final String MODEL_WITH_VISION = LLAMA_3_2_VISION;
    private static LC4jOllamaContainer ollamaWithVision;

    private static final String CUSTOM_MODEL_NAME = LLAMA_3_2;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            String localOllamaImageWithTools = localOllamaImage(MODEL_WITH_TOOLS);
            ollamaWithTools = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImageWithTools))
                    .withModel(MODEL_WITH_TOOLS)
                    .withModel(CUSTOM_MODEL_NAME);
            ollamaWithTools.start();
            ollamaWithTools.commitToImage(localOllamaImageWithTools);

            String localOllamaImageWithVision = localOllamaImage(MODEL_WITH_VISION);
            ollamaWithVision = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImageWithVision))
                    .withModel(MODEL_WITH_VISION)
                    .withModel(CUSTOM_MODEL_NAME);
            ollamaWithVision.start();
            ollamaWithVision.commitToImage(localOllamaImageWithVision);

            CONTAINER_MAP.put(localOllamaImageWithTools, ollamaWithTools);
            CONTAINER_MAP.put(localOllamaImageWithVision, ollamaWithVision);
        }
    }

    static final OllamaResponsesChatModel OLLAMA_RESPONSES_CHAT_MODEL_WITH_TOOLS =
            OllamaResponsesChatModel.builder()
                    .baseUrl(ollamaBaseUrl(ollamaWithTools))
                    .modelName(MODEL_WITH_TOOLS)
                    .temperature(0.0)
                    .logRequests(true)
                    .logResponses(true)
                    .timeout(ofSeconds(180))
                    .build();

    static final OllamaResponsesChatModel OLLAMA_RESPONSES_CHAT_MODEL_WITH_VISION =
            OllamaResponsesChatModel.builder()
                    .baseUrl(ollamaBaseUrl(ollamaWithVision))
                    .modelName(MODEL_WITH_VISION)
                    .temperature(0.0)
                    .logRequests(false) // base64-encoded images are huge in logs
                    .logResponses(true)
                    .timeout(ofSeconds(180))
                    .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(OLLAMA_RESPONSES_CHAT_MODEL_WITH_TOOLS);
    }

    @Override
    protected List<ChatModel> modelsSupportingImageInputs() {
        return List.of(OLLAMA_RESPONSES_CHAT_MODEL_WITH_VISION);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        String modelName = getOrDefault(parameters.modelName(), LLAMA_3_1);
        String localOllamaImage = localOllamaImage(modelName);
        if (!CONTAINER_MAP.containsKey(localOllamaImage) && isNullOrEmpty(OLLAMA_BASE_URL)) {
            LC4jOllamaContainer ollamaContainer =
                    new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImage)).withModel(modelName);
            ollamaContainer.start();
            ollamaContainer.commitToImage(localOllamaImage);

            CONTAINER_MAP.put(localOllamaImage, ollamaContainer);
        }

        OllamaResponsesChatModel.Builder builder = OllamaResponsesChatModel.builder()
                .baseUrl(ollamaBaseUrl(CONTAINER_MAP.get(localOllamaImage)))
                .defaultRequestParameters(parameters)
                .logRequests(true)
                .logResponses(true);

        if (parameters.modelName() == null) {
            builder.modelName(modelName);
        }

        return builder.build();
    }

    @Override
    protected String customModelName() {
        return CUSTOM_MODEL_NAME;
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OllamaResponsesChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        return false; // Ollama does not support tool_choice: required
    }

    @Override
    protected boolean supportsMultipleImageInputsAsBase64EncodedStrings() {
        return false; // vision model only supports a single image per message
    }

    @Override
    protected boolean supportsMultipleImageInputsAsPublicURLs() {
        return false; // vision model only supports a single image per message
    }

    @Override
    protected boolean assertResponseId() {
        return true; // Responses API returns an id field
    }

    @Override
    protected boolean assertToolId(ChatModel model) {
        return true; // Responses API returns call_id for tool calls
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel chatModel) {
        return ChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return TokenUsage.class;
    }
}
