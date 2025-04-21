package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Disabled;

import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_1;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_2_VISION;
import static dev.langchain4j.model.ollama.OllamaImage.OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.localOllamaImage;
import static dev.langchain4j.model.ollama.OllamaImage.resolve;

class OllamaChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    private static final String MODEL_WITH_TOOLS = LLAMA_3_1;
    private static LC4jOllamaContainer ollamaWithTools;

    private static final String MODEL_WITH_VISION = LLAMA_3_2_VISION;
    private static LC4jOllamaContainer ollamaWithVision;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            String localOllamaImageWithTools = localOllamaImage(MODEL_WITH_TOOLS);
            ollamaWithTools = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImageWithTools))
                    .withModel(MODEL_WITH_TOOLS);
            ollamaWithTools.start();
            ollamaWithTools.commitToImage(localOllamaImageWithTools);

            String localOllamaImageWithVision = localOllamaImage(MODEL_WITH_VISION);
            ollamaWithVision = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImageWithVision))
                    .withModel(MODEL_WITH_VISION);
            ollamaWithVision.start();
            ollamaWithVision.commitToImage(localOllamaImageWithVision);
        }
    }

    static final OllamaChatModel OLLAMA_CHAT_MODEL_WITH_TOOLS = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithTools))
            .modelName(MODEL_WITH_TOOLS)
            .temperature(0.0)
            .build();

    static final OllamaChatModel OLLAMA_CHAT_MODEL_WITH_VISION = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithVision))
            .modelName(MODEL_WITH_VISION)
            .temperature(0.0)
            .build();

    static final OpenAiChatModel OPEN_AI_CHAT_MODEL_WITH_TOOLS = OpenAiChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithTools) + "/v1")
            .modelName(MODEL_WITH_TOOLS)
            .temperature(0.0)
            .build();

    static final OpenAiChatModel OPEN_AI_CHAT_MODEL_WITH_VISION = OpenAiChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithVision) + "/v1")
            .modelName(MODEL_WITH_VISION)
            .temperature(0.0)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(
                OLLAMA_CHAT_MODEL_WITH_TOOLS,
                OPEN_AI_CHAT_MODEL_WITH_TOOLS
                // TODO add more model configs, see OpenAiChatModelIT
        );
    }

    @Override
    protected List<ChatModel> modelsSupportingImageInputs() {
        return List.of(
                OLLAMA_CHAT_MODEL_WITH_VISION,
                OPEN_AI_CHAT_MODEL_WITH_VISION
                // TODO add more model configs, see OpenAiChatModelIT
        );
    }

    @Override
    protected void should_fail_if_modelName_is_not_supported(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_modelName_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_maxOutputTokens_parameter_is_not_supported(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_maxOutputTokens_parameter_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_stopSequences_parameter_is_not_supported(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_stopSequences_parameter_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_tool_choice_REQUIRED_is_not_supported(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_tool_choice_REQUIRED_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_JSON_response_format_is_not_supported(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_JSON_response_format_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_JSON_response_format_with_schema_is_not_supported(model);
    }

    @Override
    @Disabled("enable after validation is implemented in OllamaChatModel")
    protected void should_fail_if_images_as_public_URLs_are_not_supported(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return;
        }
        super.should_fail_if_images_as_public_URLs_are_not_supported(model);
    }

    @Override
    protected boolean supportsDefaultRequestParameters() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsMaxOutputTokensParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false; // TODO implement
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
    protected boolean assertChatResponseMetadataType() {
        return false; // TODO fix
    }

    @Override
    protected boolean assertResponseId() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertResponseModel() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertTokenUsage() {
        return false; // TODO fix
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO implement
    }
}
