package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
            .apiKey("does not matter")
            .baseUrl(ollamaBaseUrl(ollamaWithTools) + "/v1")
            .modelName(MODEL_WITH_TOOLS)
            .temperature(0.0)
            .build();

    static final OpenAiChatModel OPEN_AI_CHAT_MODEL_WITH_VISION = OpenAiChatModel.builder()
            .apiKey("does not matter")
            .baseUrl(ollamaBaseUrl(ollamaWithVision) + "/v1")
            .modelName(MODEL_WITH_VISION)
            .temperature(0.0)
            .build();

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_TOOLS, OPEN_AI_CHAT_MODEL_WITH_TOOLS);
    }

    @Override
    protected List<ChatLanguageModel> modelsSupportingTools() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_TOOLS, OPEN_AI_CHAT_MODEL_WITH_TOOLS);
    }

    @Override
    protected List<ChatLanguageModel> modelsSupportingVision() {
        return List.of(OLLAMA_CHAT_MODEL_WITH_VISION, OPEN_AI_CHAT_MODEL_WITH_VISION);
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_JSON_response_format_is_not_supported(ChatLanguageModel model) {
        // TODO explain
        if (!(model instanceof OpenAiChatModel)) {
            super.should_fail_if_JSON_response_format_is_not_supported(model);
        }
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(ChatLanguageModel model) {
        // TODO explain
        if (!(model instanceof OpenAiChatModel)) {
            super.should_fail_if_JSON_response_format_with_schema_is_not_supported(model);
        }
    }

    @Override
    protected boolean supportsToolChoiceAnyWithMultipleTools() {
        return false; // TODO check if Ollama supports this
    }

    @Override
    protected boolean supportsToolChoiceAnyWithSingleTool() {
        return false; // TODO check if Ollama supports this
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false; // Ollama does not support structured outputs
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO fix
    }
}