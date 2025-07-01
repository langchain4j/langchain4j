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
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
            .logRequests(false) // base64-encoded images are huge in logs
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    static final OpenAiChatModel OPEN_AI_CHAT_MODEL_WITH_TOOLS = OpenAiChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithTools) + "/v1")
            .modelName(MODEL_WITH_TOOLS)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    static final OpenAiChatModel OPEN_AI_CHAT_MODEL_WITH_VISION = OpenAiChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithVision) + "/v1")
            .modelName(MODEL_WITH_VISION)
            .temperature(0.0)
            .logRequests(false) // base64-encoded images are huge in logs
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    @Override
    protected List<ChatModel> models() {
        // FIXME: to support customModelName(), all models should pull custom model
        return List.of(OLLAMA_CHAT_MODEL_WITH_TOOLS, OPEN_AI_CHAT_MODEL_WITH_TOOLS);
    }

    @Override
    protected List<ChatModel> modelsSupportingImageInputs() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_VISION, OPEN_AI_CHAT_MODEL_WITH_VISION);
    }

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(ChatModel model) {
    }

    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    @DisabledIf("supportsToolChoiceRequired")
    protected void should_fail_if_tool_choice_REQUIRED_is_not_supported(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return; // OpenAI supports it
        }
        super.should_fail_if_tool_choice_REQUIRED_is_not_supported(model);
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsJsonResponseFormat")
    protected void should_fail_if_JSON_response_format_is_not_supported(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return; // OpenAI supports it
        }
        super.should_fail_if_JSON_response_format_is_not_supported(model);
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsJsonResponseFormatWithSchema")
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return; // OpenAI supports it
        }
        super.should_fail_if_JSON_response_format_with_schema_is_not_supported(model);
    }

    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsSingleImageInputAsPublicURL")
    protected void should_accept_single_image_as_public_URL(ChatModel model) {
        if (model instanceof OpenAiChatModel) {
            return; // OpenAI does not implement automatic image download
        }
        super.should_accept_single_image_as_public_URL(model);
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
    protected boolean supportsToolChoiceRequired() {
        return false; // Ollama does not support tool choice
        // also for OpenAI-compatible API: https://github.com/ollama/ollama/blob/main/docs/openai.md
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
        return false; // Ollama does not return response ID
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel chatModel) {
        if (chatModel instanceof OpenAiChatModel) {
            return OpenAiChatResponseMetadata.class;
        } else if (chatModel instanceof OllamaChatModel) {
            return ChatResponseMetadata.class;
        } else {
            throw new IllegalStateException("Unknown model type: " + chatModel.getClass());
        }
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        if (chatModel instanceof OpenAiChatModel) {
            return OpenAiTokenUsage.class;
        } else if (chatModel instanceof OllamaChatModel) {
            return TokenUsage.class;
        } else {
            throw new IllegalStateException("Unknown model type: " + chatModel.getClass());
        }
    }
}
