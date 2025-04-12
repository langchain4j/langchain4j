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

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;

class OllamaChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

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

    static final OllamaChatModel OLLAMA_CHAT_MODEL_WITH_TOOLS = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithTools))
            .modelName(MODEL_WITH_TOOLS)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    static final OllamaChatModel OLLAMA_CHAT_MODEL_WITH_VISION = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithVision))
            .modelName(MODEL_WITH_VISION)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    static final OpenAiChatModel OPEN_AI_CHAT_MODEL_WITH_TOOLS = OpenAiChatModel.builder()
            .apiKey("does not matter")
            .baseUrl(ollamaBaseUrl(ollamaWithTools) + "/v1")
            .modelName(MODEL_WITH_TOOLS)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    static final OpenAiChatModel OPEN_AI_CHAT_MODEL_WITH_VISION = OpenAiChatModel.builder()
            .apiKey("does not matter")
            .baseUrl(ollamaBaseUrl(ollamaWithVision) + "/v1")
            .modelName(MODEL_WITH_VISION)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    @Override
    protected List<ChatLanguageModel> models() {
        // FIXME: to support customModelName(), all models should pull custom model
        return List.of(
                OLLAMA_CHAT_MODEL_WITH_TOOLS, OPEN_AI_CHAT_MODEL_WITH_TOOLS
                // TODO add more model configs, see OpenAiChatModelIT
                );
    }

    @Override
    protected List<ChatLanguageModel> modelsSupportingImageInputs() {
        return List.of(
                OLLAMA_CHAT_MODEL_WITH_VISION, OPEN_AI_CHAT_MODEL_WITH_VISION
                // TODO add more model configs, see OpenAiChatModelIT
                );
    }

    @Override
    protected void should_fail_if_tool_choice_REQUIRED_is_not_supported(ChatLanguageModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_tool_choice_REQUIRED_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_JSON_response_format_is_not_supported(ChatLanguageModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_JSON_response_format_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(ChatLanguageModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_JSON_response_format_with_schema_is_not_supported(model);
    }

    @Override
    @Disabled("enable after validation is implemented in OllamaChatModel")
    protected void should_fail_if_images_as_public_URLs_are_not_supported(ChatLanguageModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_images_as_public_URLs_are_not_supported(model);
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        String modelName = getOrDefault(parameters.modelName(), LLAMA_3_1);
        String localOllamaImage = localOllamaImage(modelName);
        if (!CONTAINER_MAP.containsKey(localOllamaImage) && isNullOrEmpty(OLLAMA_BASE_URL)) {
            LC4jOllamaContainer ollamaContainer =
                    new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImage)).withModel(modelName);
            ollamaContainer.start();
            ollamaContainer.commitToImage(localOllamaImage);

            CONTAINER_MAP.put(localOllamaImage, ollamaContainer);
        }

        OllamaChatModel.OllamaChatModelBuilder ollamaChatModelBuilder = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(CONTAINER_MAP.get(localOllamaImage)))
                .defaultRequestParameters(parameters)
                .logRequests(true)
                .logResponses(true);

        if (parameters.modelName() == null) {
            ollamaChatModelBuilder.modelName(modelName);
        }

        return ollamaChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return CUSTOM_MODEL_NAME;
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OllamaChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO: Ollama does not support TOOL_EXECUTION finish reason.
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        return false; // TODO check if Ollama supports this
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return false; // TODO fix
    }

    @Override
    protected boolean supportsMultipleImageInputsAsBase64EncodedStrings() {
        return false; // vision model only supports a single image per message
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false; // Ollama supports only base64-encoded images
    }

    @Override
    protected boolean supportsMultipleImageInputsAsPublicURLs() {
        return false; // Ollama supports only base64-encoded images
    }

    @Override
    protected boolean assertResponseId() {
        return false; // TODO implement
    }
}
