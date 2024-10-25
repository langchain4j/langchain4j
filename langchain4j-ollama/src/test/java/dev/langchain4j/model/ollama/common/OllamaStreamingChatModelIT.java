package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModelIT;
import dev.langchain4j.model.ollama.LC4jOllamaContainer;
import dev.langchain4j.model.ollama.OllamaImage;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.util.List;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.LOCAL_OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;

class OllamaStreamingChatModelIT extends StreamingChatLanguageModelIT {

    static LC4jOllamaContainer ollama = new LC4jOllamaContainer(
            OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE)
    ).withModel(OllamaImage.TINY_DOLPHIN_MODEL);

    static {
        ollama.start();
        ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
    }

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                OllamaStreamingChatModel.builder()
                        .baseUrl(ollama.getEndpoint())
                        .modelName(TINY_DOLPHIN_MODEL)
                        .temperature(0.0)
                        .build(),
                OpenAiStreamingChatModel.builder()
                        .apiKey("does not matter") // TODO make apiKey optional when using custom baseUrl?
                        .baseUrl(ollama.getEndpoint() + "/v1") // TODO add "/v1" by default?
                        .modelName(TINY_DOLPHIN_MODEL)
                        .temperature(0.0)
                        .build()
        );
    }

    @Override
    protected boolean assertTokenUsage() {
        return false; // TODO why?
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO why?
    }
}
