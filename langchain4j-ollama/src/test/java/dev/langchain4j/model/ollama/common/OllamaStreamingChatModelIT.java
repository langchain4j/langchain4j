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

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OllamaStreamingChatModelIT extends AbstractStreamingChatModelIT {

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

    static final OllamaStreamingChatModel OLLAMA_CHAT_MODEL_WITH_TOOLS = OllamaStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithTools))
            .modelName(MODEL_WITH_TOOLS)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    static final OllamaStreamingChatModel OLLAMA_CHAT_MODEL_WITH_VISION = OllamaStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithVision))
            .modelName(MODEL_WITH_VISION)
            .temperature(0.0)
            .logRequests(false) // base64-encoded images are huge in logs
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    static final OpenAiStreamingChatModel OPEN_AI_CHAT_MODEL_WITH_TOOLS = OpenAiStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithTools) + "/v1")
            .modelName(MODEL_WITH_TOOLS)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    static final OpenAiStreamingChatModel OPEN_AI_CHAT_MODEL_WITH_VISION = OpenAiStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollamaWithVision) + "/v1")
            .modelName(MODEL_WITH_VISION)
            .temperature(0.0)
            .logRequests(false) // base64-encoded images are huge in logs
            .logResponses(true)
            .timeout(ofSeconds(180))
            .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_TOOLS, OPEN_AI_CHAT_MODEL_WITH_TOOLS);
    }

    @Override
    protected List<StreamingChatModel> modelsSupportingImageInputs() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_VISION, OPEN_AI_CHAT_MODEL_WITH_VISION);
    }

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(StreamingChatModel model) {
    }

    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    @DisabledIf("supportsToolChoiceRequired")
    protected void should_fail_if_tool_choice_REQUIRED_is_not_supported(StreamingChatModel model) {
        if (model instanceof OpenAiStreamingChatModel) {
            return; // OpenAI supports it
        }
        super.should_fail_if_tool_choice_REQUIRED_is_not_supported(model);
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsJsonResponseFormat")
    protected void should_fail_if_JSON_response_format_is_not_supported(StreamingChatModel model) {
        if (model instanceof OpenAiStreamingChatModel) {
            return; // OpenAI supports it
        }
        super.should_fail_if_JSON_response_format_is_not_supported(model);
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsJsonResponseFormatWithSchema")
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(StreamingChatModel model) {
        if (model instanceof OpenAiStreamingChatModel) {
            return; // OpenAI supports it
        }
        super.should_fail_if_JSON_response_format_with_schema_is_not_supported(model);
    }

    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsSingleImageInputAsPublicURL")
    protected void should_accept_single_image_as_public_URL(StreamingChatModel model) {
        if (model instanceof OpenAiStreamingChatModel) {
            return; // OpenAI does not implement automatic image download
        }
        super.should_accept_single_image_as_public_URL(model);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        String modelName = getOrDefault(parameters.modelName(), LLAMA_3_1);
        String localOllamaImage = localOllamaImage(modelName);
        if (!CONTAINER_MAP.containsKey(localOllamaImage) && isNullOrEmpty(OLLAMA_BASE_URL)) {
            LC4jOllamaContainer ollamaContainer =
                    new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImage)).withModel(modelName);
            ollamaContainer.start();
            ollamaContainer.commitToImage(localOllamaImage);

            CONTAINER_MAP.put(localOllamaImage, ollamaContainer);
        }

        OllamaStreamingChatModel.OllamaStreamingChatModelBuilder ollamaStreamingChatModelBuilder =
                OllamaStreamingChatModel.builder()
                        .baseUrl(ollamaBaseUrl(CONTAINER_MAP.get(localOllamaImage)))
                        .defaultRequestParameters(parameters)
                        .logRequests(true)
                        .logResponses(true);

        if (parameters.modelName() == null) {
            ollamaStreamingChatModelBuilder.modelName(modelName);
        }

        return ollamaStreamingChatModelBuilder.build();
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
    protected boolean assertTimesOnPartialResponseWasCalled() {
        return false; // Ollama responds with a single SSE event for some reason (perhaps due to tools)
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollamaWithTools))
                .modelName(MODEL_WITH_TOOLS)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .timeout(ofSeconds(180))
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        if (streamingChatModel instanceof OpenAiStreamingChatModel) {
            return OpenAiChatResponseMetadata.class;
        } else if (streamingChatModel instanceof OllamaStreamingChatModel) {
            return ChatResponseMetadata.class;
        } else {
            throw new IllegalStateException("Unknown model type: " + streamingChatModel.getClass());
        }
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        if (streamingChatModel instanceof OpenAiStreamingChatModel) {
            return OpenAiTokenUsage.class;
        } else if (streamingChatModel instanceof OllamaStreamingChatModel) {
            return TokenUsage.class;
        } else {
            throw new IllegalStateException("Unknown model type: " + streamingChatModel.getClass());
        }
    }
}
