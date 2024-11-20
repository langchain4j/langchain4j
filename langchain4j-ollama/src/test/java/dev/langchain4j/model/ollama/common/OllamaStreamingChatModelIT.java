package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.LOCAL_OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static dev.langchain4j.model.ollama.OllamaImage.resolve;

class OllamaStreamingChatModelIT extends AbstractStreamingChatModelIT {

    private static final String MODEL_NAME = TINY_DOLPHIN_MODEL;

    private static LC4jOllamaContainer ollama;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            ollama = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
                    .withModel(MODEL_NAME);
            ollama.start();
            ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
        }
    }

    static final OllamaStreamingChatModel OLLAMA_STREAMING_CHAT_MODEL = OllamaStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName(MODEL_NAME)
            .temperature(0.0)
            .build();

    static final OpenAiStreamingChatModel OPEN_AI_STREAMING_CHAT_MODEL = OpenAiStreamingChatModel.builder()
            .apiKey("does not matter") // TODO make apiKey optional when using custom baseUrl?
            .baseUrl(ollamaBaseUrl(ollama) + "/v1") // TODO add "/v1" by default?
            .modelName(MODEL_NAME)
            .temperature(0.0)
            .build();

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(OLLAMA_STREAMING_CHAT_MODEL, OPEN_AI_STREAMING_CHAT_MODEL);
    }

    @Override
    protected boolean assertTokenUsage() {
        return false; // TODO fix
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO fix
    }

    @Override
    protected boolean supportsTools() {
        return false; // Ollama does not support tools in streaming mode
    }
}
