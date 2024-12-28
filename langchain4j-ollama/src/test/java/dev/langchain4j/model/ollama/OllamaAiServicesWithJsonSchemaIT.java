package dev.langchain4j.model.ollama;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_1;
import static dev.langchain4j.model.ollama.OllamaImage.OLLAMA_IMAGE;
import static dev.langchain4j.model.ollama.OllamaImage.localOllamaImage;
import static dev.langchain4j.model.ollama.OllamaImage.resolve;

class OllamaAiServicesWithJsonSchemaIT extends AiServicesWithJsonSchemaIT {

    private static final String MODEL = LLAMA_3_1;
    private static LC4jOllamaContainer ollama;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            String localOllamaImageWithTools = localOllamaImage(MODEL);
            ollama = new LC4jOllamaContainer(resolve(OLLAMA_IMAGE, localOllamaImageWithTools)).withModel(MODEL);
            ollama.start();
            ollama.commitToImage(localOllamaImageWithTools);
        }
    }

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(LLAMA_3_1)
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());
    }

    @Test
    @Disabled("llama 3.1 is cannot do it properly")
    @Override
    protected void should_extract_pojo_with_nested_pojo() {
    }

    @Test
    @Disabled("llama 3.1 is cannot do it properly")
    @Override
    protected void should_extract_pojo_with_list_of_pojos() {
    }

    @Test
    @Disabled("llama 3.1 is cannot do it properly")
    @Override
    protected void should_extract_pojo_with_array_of_pojos() {
    }

    @Test
    @Disabled("llama 3.1 is cannot do it properly")
    @Override
    protected void should_extract_pojo_with_set_of_pojos() {
    }
}
