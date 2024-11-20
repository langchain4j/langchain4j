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
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.LOCAL_OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.TOOL_MODEL;
import static dev.langchain4j.model.ollama.OllamaImage.resolve;

class OllamaChatModelIT extends AbstractChatModelIT {

    private static final String MODEL_NAME = TOOL_MODEL;

    static LC4jOllamaContainer ollama;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            ollama = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
                    .withModel(MODEL_NAME);
            ollama.start();
            ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
        }
    }

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                OllamaChatModel.builder()
                        .baseUrl(ollamaBaseUrl(ollama))
                        .modelName(MODEL_NAME)
                        .temperature(0.0)
                        .build(),
                OpenAiChatModel.builder()
                        .apiKey("does not matter") // TODO make apiKey optional when using custom baseUrl?
                        .baseUrl(ollamaBaseUrl(ollama) + "/v1") // TODO add "/v1" by default?
                        .modelName(MODEL_NAME)
                        .temperature(0.0)
                        .build()
        );
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_JSON_response_format_is_not_supported(ChatLanguageModel model) {
        if (!(model instanceof OpenAiChatModel)) {
            super.should_fail_if_JSON_response_format_is_not_supported(model);
        }
    }

    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(ChatLanguageModel model) {
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
