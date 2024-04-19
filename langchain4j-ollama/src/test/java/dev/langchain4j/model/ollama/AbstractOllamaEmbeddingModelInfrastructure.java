package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.OllamaImage.ALL_MINILM_MODEL;
import static dev.langchain4j.model.ollama.OllamaImage.OLLAMA_IMAGE;

class AbstractOllamaEmbeddingModelInfrastructure {

    private static final String LOCAL_OLLAMA_IMAGE = String.format("tc-%s-%s", OLLAMA_IMAGE, ALL_MINILM_MODEL);

    static LangChain4jOllamaContainer ollama;

    static {
        ollama = new LangChain4jOllamaContainer(OllamaImage.resolve(OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
                .withModel(ALL_MINILM_MODEL);
        ollama.start();
        ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
    }
}
