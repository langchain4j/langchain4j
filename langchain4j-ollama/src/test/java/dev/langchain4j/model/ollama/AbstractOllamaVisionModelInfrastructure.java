package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.OllamaImage.GEMMA_4;
import static dev.langchain4j.model.ollama.OllamaImage.localOllamaImage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class AbstractOllamaVisionModelInfrastructure {

    protected static final String MODEL_NAME = GEMMA_4;

    static LC4jOllamaContainer ollama;

    // Started before and stopped after each test class, so that a finished test class does not keep its
    // container (and the loaded model) in memory. A static initializer would run only once for all subclasses.
    @BeforeAll
    static void startOllama() {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            String localOllamaImage = localOllamaImage(MODEL_NAME);
            ollama = new LC4jOllamaContainer(OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, localOllamaImage))
                    .withModel(MODEL_NAME);
            ollama.start();
            ollama.commitToImage(localOllamaImage);
        }
    }

    @AfterAll
    static void stopOllama() {
        if (ollama != null) {
            ollama.stop();
        }
    }
}
