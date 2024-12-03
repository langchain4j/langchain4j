package dev.langchain4j.model.ollama;

import dev.langchain4j.service.AiServicesWithNewToolsIT;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;

abstract class AbstractOllamaToolsLanguageModelInfrastructure extends AiServicesWithNewToolsIT {

    private static final String LOCAL_OLLAMA_IMAGE = String.format("tc-%s-%s", OllamaImage.OLLAMA_IMAGE, OllamaImage.TOOL_MODEL);

    static LangChain4jOllamaContainer ollama;

    static {
        String ollamaBaseUrl = System.getenv("OLLAMA_BASE_URL");
        if (isNullOrEmpty(ollamaBaseUrl)) {
            ollama = new LangChain4jOllamaContainer(OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
                .withModel(OllamaImage.TOOL_MODEL);
            ollama.start();
            ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
        }
    }

    public static String ollamaBaseUrl() {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            return ollama.getEndpoint();
        } else {
            return OLLAMA_BASE_URL;
        }
    }
}
