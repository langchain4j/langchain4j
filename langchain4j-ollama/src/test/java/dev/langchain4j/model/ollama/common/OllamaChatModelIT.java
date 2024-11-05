package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.List;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.LOCAL_OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.TOOL_MODEL;
import static dev.langchain4j.model.ollama.OllamaImage.resolve;

class OllamaChatModelIT extends AbstractChatModelIT {

    static String MODEL_NAME = TOOL_MODEL;

    static LC4jOllamaContainer ollama = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
            .withModel(MODEL_NAME);

    static {
        ollama.start();
        ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
    }

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                OllamaChatModel.builder()
                        .baseUrl(ollama.getEndpoint())
                        .modelName(MODEL_NAME)
                        .temperature(0.0)
                        .build(),
                OpenAiChatModel.builder()
                        .apiKey("does not matter") // TODO make apiKey optional when using custom baseUrl?
                        .baseUrl(ollama.getEndpoint() + "/v1") // TODO add "/v1" by default?
                        .modelName(MODEL_NAME)
                        .temperature(0.0)
                        .build()
        );
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO fix
    }
}
