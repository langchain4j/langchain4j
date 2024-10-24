package dev.langchain4j.model.ollama;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesSimpleIT;

import java.util.List;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.LOCAL_OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;

class OllamaAiServicesSimpleIT extends AiServicesSimpleIT {

    static LangChain4jOllamaContainer ollama = new LangChain4jOllamaContainer(OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
            .withModel(OllamaImage.TINY_DOLPHIN_MODEL);

    static {
        ollama.start();
        ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
    }

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                OllamaChatModel.builder()
                        .baseUrl(ollama.getEndpoint())
                        .modelName(TINY_DOLPHIN_MODEL)
                        .logResponses(true)
                        .build()
        );
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }
}
