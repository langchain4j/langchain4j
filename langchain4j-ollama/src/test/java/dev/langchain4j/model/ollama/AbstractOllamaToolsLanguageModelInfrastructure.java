package dev.langchain4j.model.ollama;

import dev.langchain4j.service.AiServicesWithNewToolsIT;

abstract class AbstractOllamaToolsLanguageModelInfrastructure extends AiServicesWithNewToolsIT {

    private static final String LOCAL_OLLAMA_IMAGE = "tc-%s-%s".formatted(OllamaImage.OLLAMA_IMAGE, OllamaImage.TOOL_MODEL);

    static LangChain4jOllamaContainer ollama;

    static {
        ollama = new LangChain4jOllamaContainer(OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE))
                .withModel(OllamaImage.TOOL_MODEL);
        ollama.start();
        ollama.commitToImage(LOCAL_OLLAMA_IMAGE);
    }
}
