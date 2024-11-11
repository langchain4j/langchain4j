package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

class AbstractOllamaLanguageModelInfrastructure {

    static final String OLLAMA_BASE_URL = System.getenv("OLLAMA_BASE_URL");

    private static final String LOCAL_OLLAMA_IMAGE = String.format("tc-%s-%s", OllamaImage.OLLAMA_IMAGE, OllamaImage.TINY_DOLPHIN_MODEL);

    static LangChain4jOllamaContainer ollama;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            ollama = new LangChain4jOllamaContainer(OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
                .withModel(OllamaImage.TINY_DOLPHIN_MODEL);
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
