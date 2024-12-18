package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
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

class OllamaStreamingChatModelIT extends AbstractStreamingChatModelIT {

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

    static final OllamaStreamingChatModel OLLAMA_CHAT_MODEL_WITH_TOOLS = OllamaStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithTools))
            .modelName(MODEL_WITH_TOOLS)
            .temperature(0.0)
            .build();

    static final OllamaStreamingChatModel OLLAMA_CHAT_MODEL_WITH_VISION = OllamaStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithVision))
            .modelName(MODEL_WITH_VISION)
            .temperature(0.0)
            .build();

    static final OpenAiStreamingChatModel OPEN_AI_CHAT_MODEL_WITH_TOOLS = OpenAiStreamingChatModel.builder()
            .apiKey("does not matter")
            .baseUrl(ollamaBaseUrl(ollamaWithTools) + "/v1")
            .modelName(MODEL_WITH_TOOLS)
            .temperature(0.0)
            .build();

    static final OpenAiStreamingChatModel OPEN_AI_CHAT_MODEL_WITH_VISION = OpenAiStreamingChatModel.builder()
            .apiKey("does not matter")
            .baseUrl(ollamaBaseUrl(ollamaWithVision) + "/v1")
            .modelName(MODEL_WITH_VISION)
            .temperature(0.0)
            .build();

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                OLLAMA_CHAT_MODEL_WITH_TOOLS,
                OPEN_AI_CHAT_MODEL_WITH_TOOLS
                // TODO add more model configs, see OpenAiChatModelIT
        );
    }

    @Override
    protected List<StreamingChatLanguageModel> modelsSupportingImageInputs() {
        return List.of(
                OLLAMA_CHAT_MODEL_WITH_VISION,
                OPEN_AI_CHAT_MODEL_WITH_VISION
                // TODO add more model configs, see OpenAiChatModelIT
        );
    }

    @Override
    protected void should_fail_if_stopSequences_parameter_is_not_supported(StreamingChatLanguageModel model) {
        if (model instanceof OpenAiStreamingChatModel) {
            return;
        }
        super.should_fail_if_stopSequences_parameter_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_maxOutputTokens_parameter_is_not_supported(StreamingChatLanguageModel model) {
        if (model instanceof OpenAiStreamingChatModel) {
            return;
        }
        super.should_fail_if_maxOutputTokens_parameter_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_modelName_is_not_supported(StreamingChatLanguageModel model) {
        if (model instanceof OpenAiStreamingChatModel) {
            return;
        }
        super.should_fail_if_modelName_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_JSON_response_format_is_not_supported(StreamingChatLanguageModel model) {
        if (model instanceof OpenAiStreamingChatModel) {
            return;
        }
        super.should_fail_if_JSON_response_format_is_not_supported(model);
    }

    @Override
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(StreamingChatLanguageModel model) {
        if (model instanceof OpenAiStreamingChatModel) {
            return;
        }
        super.should_fail_if_JSON_response_format_with_schema_is_not_supported(model);
    }

    @Override
    @Disabled("enable after validation is implemented in OllamaStreamingChatModel")
    protected void should_fail_if_images_as_public_URLs_are_not_supported(StreamingChatLanguageModel model) {
        if (model instanceof OpenAiStreamingChatModel) {
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
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false; // TODO check if Ollama supports this
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithSingleTool() {
        return false; // TODO check if Ollama supports this
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false; // TODO implement
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

    @Override
    protected boolean assertResponseModel() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertTokenUsage() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertTimesOnPartialResponseWasCalled() {
        return false; // TODO
    }
}
